############################################
# A static web app (nginx + built SPA) as one more ECS service on an
# existing cluster and ALB (owned by ecs-ec2-app).
############################################
# This module adds, per app: an ECR repo, a log group, a task definition, a
# target group, a host-header rule on the ALB's public listener, an ECS
# service, and optionally an ACM certificate attached to the :443 listener
# by SNI. It never touches the shared cluster, ALB, security groups or host:
# the host SG already admits the ALB on the dynamic port range, and the task
# reuses the shared task execution role.
#
# Images are shipped by GitHub Actions (frontend-deploy-<env>.yml); like the
# backend service, Terraform ignores task_definition and desired_count.

data "aws_region" "current" {}

resource "aws_ecr_repository" "app" {
  name                 = var.app_name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = var.tags
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.app_name}"
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

resource "aws_ecs_task_definition" "app" {
  family                   = var.app_name
  requires_compatibilities = ["EC2"]
  network_mode             = "bridge"
  memory                   = var.task_memory
  execution_role_arn       = var.task_execution_role_arn

  container_definitions = jsonencode([{
    name      = var.app_name
    image     = var.placeholder_image
    essential = true
    portMappings = [{
      containerPort = var.container_port
      hostPort      = 0 # dynamic; the target group tracks it
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

resource "aws_lb_target_group" "app" {
  name                 = "${var.app_name}-tg"
  port                 = var.container_port
  protocol             = "HTTP"
  vpc_id               = var.vpc_id
  target_type          = "instance"
  deregistration_delay = 15 # static files: nothing long-running to drain

  health_check {
    path                = var.health_check_path
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
    matcher             = "200"
  }

  tags = var.tags
}

# Host-based routing: requests for var.host_name go to this app, everything
# else keeps hitting the listener's default action (the API).
resource "aws_lb_listener_rule" "host" {
  listener_arn = var.listener_arn
  priority     = var.listener_rule_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  condition {
    host_header {
      values = [var.host_name]
    }
  }

  tags = var.tags
}

resource "aws_ecs_service" "app" {
  name            = "${var.app_name}-service"
  cluster         = var.ecs_cluster_arn
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1

  # nginx is small enough to run old + new side by side on the host, so
  # deploys start the new task before stopping the old one: no downtime.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 30

  capacity_provider_strategy {
    capacity_provider = var.capacity_provider_name
    weight            = 100
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.app_name
    container_port   = var.container_port
  }

  # GitHub Actions owns the running revision; env-power.sh owns the count.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener_rule.host]

  tags = var.tags
}

############################################
# Optional HTTPS certificate for var.host_name, attached to the shared :443
# listener by SNI (the listener keeps its default certificate for the API).
# Same two steps as ecs-ec2-app/https.tf:
#   1. request_certificate = true  -> apply, add acm_validation_records to DNS
#   2. attach_certificate  = true  -> apply (waits for ISSUED, then attaches)
############################################

resource "aws_acm_certificate" "app" {
  count             = var.request_certificate ? 1 : 0
  domain_name       = var.host_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.tags, { Name = "${var.app_name}-cert" })
}

resource "aws_acm_certificate_validation" "app" {
  count           = var.attach_certificate ? 1 : 0
  certificate_arn = aws_acm_certificate.app[0].arn

  timeouts {
    create = "30m"
  }

  lifecycle {
    precondition {
      condition     = var.request_certificate && var.https_listener_arn != null
      error_message = "attach_certificate needs request_certificate = true and the shared HTTPS listener to exist (enable_https_listener in the backend env)."
    }
  }
}

resource "aws_lb_listener_certificate" "app" {
  count           = var.attach_certificate ? 1 : 0
  listener_arn    = var.https_listener_arn
  certificate_arn = aws_acm_certificate_validation.app[0].certificate_arn
}
