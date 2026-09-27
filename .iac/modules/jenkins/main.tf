############################################
# Module: jenkins
############################################
# One EC2 instance running Jenkins + Docker + AWS CLI, with an IAM role
# that lets Jenkins push images to ECR and deploy to the ECS cluster.
#
# Free tier: t3.micro/t2.micro covers 750 hrs/month for 12 months from
# account creation — running this 24/7 stays within that. After 12 months,
# it converts to standard hourly billing (~$0.0084/hr for t3.micro in
# ap-south-1 as of writing) unless you stop it or move to a paid plan.

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name = "name"
    # "2023.*" matters: a bare "al2023-ami-*" also matches the ECS/neuron/minimal
    # AMIs, and most_recent would pick one of those instead of standard AL2023.
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_security_group" "jenkins" {
  name        = "poshan-jenkins-sg"
  description = "Jenkins server access"
  vpc_id      = var.vpc_id

  ingress {
    description = "SSH (restrict to your own IP)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  ingress {
    description = "Jenkins web UI"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.jenkins_ui_allowed_cidr]
  }

  egress {
    description = "All outbound (package installs, ECR push, ECS API calls, github)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "poshan-jenkins-sg" })
}

# ---- IAM: allow Jenkins to push to ECR and deploy to ECS ----
resource "aws_iam_role" "jenkins" {
  name = "poshan-jenkins-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = var.tags
}

# ECR push/pull
resource "aws_iam_role_policy_attachment" "ecr_power_user" {
  role       = aws_iam_role.jenkins.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser"
}

# Deploy to ECS (update task definitions, force new deployment, etc.)
resource "aws_iam_role_policy_attachment" "ecs_full" {
  role       = aws_iam_role.jenkins.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonECS_FullAccess"
}

# SSM Session Manager — lets you shell into the box WITHOUT opening SSH
# to the world; nice fallback alongside the key-pair SSH access above.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.jenkins.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "jenkins" {
  name = "poshan-jenkins-instance-profile"
  role = aws_iam_role.jenkins.name
}

resource "aws_instance" "jenkins" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [aws_security_group.jenkins.id]
  iam_instance_profile   = aws_iam_instance_profile.jenkins.name
  key_name               = var.key_pair_name

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
  }

  user_data = file("${path.module}/user_data.sh.tpl")

  tags = merge(var.tags, { Name = "poshan-jenkins" })
}

# Elastic IP so Jenkins has a stable address (Free Tier: no charge while
# attached to a RUNNING instance; charged ~$3.6/month if left unattached,
# e.g. if you stop the instance without releasing this EIP).
resource "aws_eip" "jenkins" {
  instance = aws_instance.jenkins.id
  domain   = "vpc"
  tags     = merge(var.tags, { Name = "poshan-jenkins-eip" })
}
