############################################
# IAM: ECS container-instance role + ECS task execution role
############################################

# --- Role for the EC2 instance itself, so the ECS agent on it can
#     register with the cluster, pull images, ship logs, etc.
resource "aws_iam_role" "ecs_instance" {
  name = "${var.app_name}-ecs-instance-role"

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

resource "aws_iam_role_policy_attachment" "ecs_instance" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

resource "aws_iam_role_policy_attachment" "ecs_instance_ssm" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ecs_instance" {
  name = "${var.app_name}-ecs-instance-profile"
  role = aws_iam_role.ecs_instance.name
}

# Lets the instance attach its own fixed Elastic IP on boot (see eip.tf and
# the launch template's user_data). ec2:AssociateAddress needs both the
# instance and EIP as resources and doesn't support clean resource-level
# scoping for the pair, so this is "*" — acceptable for a single-instance
# dev host where the role itself is already scoped to this one app.
resource "aws_iam_role_policy" "ecs_instance_eip" {
  count = var.enable_static_ip ? 1 : 0
  name  = "${var.app_name}-self-associate-eip"
  role  = aws_iam_role.ecs_instance.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ec2:AssociateAddress", "ec2:DescribeAddresses"]
      Resource = "*"
    }]
  })
}

# --- Task execution role: used by ECS itself (not your app) to pull the
#     image from ECR and write logs to CloudWatch on the task's behalf.
resource "aws_iam_role" "task_execution" {
  name = "${var.app_name}-ecs-task-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Lets the task read the Secrets Manager secret holding DB creds/API keys
# (mirrors Part 4.4 of the deployment guide). Scoped to secrets named
# "<app_name>/*" rather than "*".
resource "aws_iam_role_policy" "task_execution_secrets" {
  name = "${var.app_name}-secrets-access"
  role = aws_iam_role.task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = "arn:aws:secretsmanager:*:*:secret:${var.app_name}/*"
    }]
  })
}
