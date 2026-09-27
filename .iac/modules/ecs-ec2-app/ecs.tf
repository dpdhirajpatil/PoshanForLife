############################################
# ECS: cluster + EC2 capacity (single instance) + service
############################################
# Launch type EC2 (not Fargate) — the container runs on an EC2 instance
# you own, which IS free-tier eligible (unlike Fargate compute).

resource "aws_ecs_cluster" "app" {
  name = "${var.app_name}-cluster"
  tags = var.tags
}

data "aws_ssm_parameter" "ecs_optimized_ami" {
  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}

resource "aws_security_group" "ecs_instance" {
  name        = "${var.app_name}-ecs-instance-sg"
  description = "ECS container host - only ALB and SSH may reach it"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Container port, ALB only"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Ephemeral port range so ECS's dynamic host-port mapping works if you
  # ever run more than one task per instance. Still ALB-only.
  ingress {
    description     = "ECS dynamic port mapping, ALB only"
    from_port       = 32768
    to_port         = 65535
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "SSH (restrict to your own IP)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.app_name}-ecs-instance-sg" })
}

resource "aws_launch_template" "ecs_instance" {
  name_prefix   = "${var.app_name}-ecs-"
  image_id      = data.aws_ssm_parameter.ecs_optimized_ami.value
  instance_type = var.instance_type
  key_name      = var.key_pair_name

  iam_instance_profile {
    name = aws_iam_instance_profile.ecs_instance.name
  }

  vpc_security_group_ids = [aws_security_group.ecs_instance.id]

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size = var.root_volume_size_gb
      volume_type = "gp3"
    }
  }

  # Tells the ECS agent which cluster to register this instance with.
  user_data = base64encode(<<-EOF
    #!/bin/bash
    echo ECS_CLUSTER=${aws_ecs_cluster.app.name} >> /etc/ecs/ecs.config
  EOF
  )

  tag_specifications {
    resource_type = "instance"
    tags          = merge(var.tags, { Name = "${var.app_name}-ecs-instance" })
  }
}

# Single-instance Auto Scaling Group. Using an ASG (min=max=desired=1)
# rather than a bare aws_instance because it self-heals: if the instance
# dies, the ASG replaces it automatically and it rejoins the cluster.
resource "aws_autoscaling_group" "ecs_instance" {
  name                = "${var.app_name}-ecs-asg"
  desired_capacity    = 1
  min_size            = 1
  max_size            = 1
  vpc_zone_identifier = var.subnet_ids

  launch_template {
    id      = aws_launch_template.ecs_instance.id
    version = "$Latest"
  }

  tag {
    key                 = "AmazonECSManaged"
    value               = ""
    propagate_at_launch = true
  }

  tag {
    key                 = "Name"
    value               = "${var.app_name}-ecs-instance"
    propagate_at_launch = true
  }

  # Size is owned at runtime by the ECS capacity provider and by the
  # env start/stop skill (.iac/SKILLS/poshan-env-power), which scales to 0
  # to save credits. Without this, any `terraform apply` would silently
  # restart a stopped environment.
  lifecycle {
    ignore_changes = [desired_capacity, min_size]
  }
}

resource "aws_ecs_capacity_provider" "app" {
  name = "${var.app_name}-cp"

  auto_scaling_group_provider {
    auto_scaling_group_arn = aws_autoscaling_group.ecs_instance.arn

    managed_scaling {
      status          = "ENABLED"
      target_capacity = 100
    }
  }
}

resource "aws_ecs_cluster_capacity_providers" "app" {
  cluster_name       = aws_ecs_cluster.app.name
  capacity_providers = [aws_ecs_capacity_provider.app.name]

  default_capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.app.name
    weight            = 100
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.app_name}"
  retention_in_days = 14 # keeps CloudWatch Logs storage small/free-tier-friendly

  tags = var.tags
}

# Initial task definition using a placeholder image so the whole stack
# (ALB -> ECS -> container -> health check) can be validated before
# the first GitHub Actions deploy. The workflow registers new revisions afterward.
resource "aws_ecs_task_definition" "app" {
  family                   = var.app_name
  requires_compatibilities = ["EC2"]
  network_mode             = "bridge" # simplest mode for EC2 launch type, single instance
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn

  container_definitions = jsonencode([{
    name      = var.app_name
    image     = var.placeholder_image
    essential = true
    environment = [
      for k in sort(keys(var.container_environment)) : { name = k, value = var.container_environment[k] }
    ]
    # "<secret-arn>:<json-key>::" pulls one key out of the JSON secret at task start.
    secrets = [
      for k in var.app_secret_keys : { name = k, valueFrom = "${aws_secretsmanager_secret.app[0].arn}:${k}::" }
    ]
    portMappings = [{
      containerPort = var.container_port
      hostPort      = 0 # dynamic host port; ALB tracks it via the target group
      protocol      = "tcp"
    }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = data.aws_region.current.name
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = var.tags
}

data "aws_region" "current" {}

resource "aws_ecs_service" "app" {
  name            = "${var.app_name}-service"
  cluster         = aws_ecs_cluster.app.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1

  health_check_grace_period_seconds  = var.health_check_grace_period_seconds
  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.deployment_maximum_percent

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.app.name
    weight            = 100
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.app_name
    container_port   = var.container_port
  }

  # The GitHub Actions deploy workflow registers new task definition
  # revisions and updates the service directly. Terraform should NOT fight that by
  # reverting to the placeholder image on every apply.
  # desired_count is ignored for the same reason as the ASG size above:
  # the start/stop skill sets it to 0/1.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  # The capacity provider must be attached to the cluster before the service
  # can reference it, or service creation fails on first apply.
  depends_on = [aws_lb_listener.http, aws_ecs_cluster_capacity_providers.app]

  tags = var.tags
}
