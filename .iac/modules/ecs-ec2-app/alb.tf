############################################
# ALB: stable public entrypoint (Route 53-ready)
############################################
# Free tier: 750 ALB-hours/month + 15 LCUs/month free for 12 months from
# account creation. After that, expect ~$16-20/month baseline regardless
# of traffic. HTTPS/ACM certificate is deliberately NOT set up yet — add
# it once a domain is registered (Route 53 phase); HTTP-only for now.

# When CloudFront fronts the ALB (enable_https_cdn), this locks the ALB down
# to CloudFront's own IP ranges so direct plain-HTTP access is no longer
# possible — CloudFront becomes the only way in, which is the point of
# putting it there in the first place. AWS publishes this list and keeps it
# current; it isn't a fixed CIDR we'd need to maintain.
data "aws_ec2_managed_prefix_list" "cloudfront" {
  count = var.enable_https_cdn ? 1 : 0
  name  = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name = "${var.app_name}-alb-sg"
  # Kept static on purpose: an SG's description is ForceNew, and replacing an
  # SG the ALB is attached to fails (DependencyViolation). The ingress rule
  # below is what actually changes with enable_https_cdn.
  description = "ALB - public HTTP entry"
  vpc_id      = var.vpc_id

  ingress {
    description     = var.enable_https_cdn ? "HTTP from CloudFront only" : "HTTP from anywhere"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    cidr_blocks     = var.enable_https_cdn ? null : ["0.0.0.0/0"]
    prefix_list_ids = var.enable_https_cdn ? [data.aws_ec2_managed_prefix_list.cloudfront[0].id] : null
  }

  # HTTPS listener (https.tf), when enabled.
  dynamic "ingress" {
    for_each = var.enable_https_listener ? [1] : []
    content {
      description = "HTTPS from anywhere"
      from_port   = 443
      to_port     = 443
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.app_name}-alb-sg" })
}

resource "aws_lb" "app" {
  name               = "${var.app_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.subnet_ids

  # Keep this off for a low-traffic dev/MVP setup — deletion protection
  # would block `terraform destroy` and cost nothing to leave off.
  enable_deletion_protection = false

  tags = var.tags
}

resource "aws_lb_target_group" "app" {
  name     = "${var.app_name}-tg"
  port     = var.container_port
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  deregistration_delay = var.deregistration_delay_seconds

  # target_type = "instance" because we're on ECS EC2 launch type, not Fargate.
  # (Switching to Fargate later means changing this to "ip" — see project README.)
  target_type = "instance"

  health_check {
    path                = var.health_check_path
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 10
    matcher             = "200-399"
  }

  tags = var.tags
}

# With the HTTPS listener on, :80 only redirects to https. Not while
# CloudFront fronts the ALB: its origin connection is plain HTTP, so a
# redirect there would loop.
locals {
  redirect_http_to_https = var.enable_https_listener && !var.enable_https_cdn
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = local.redirect_http_to_https ? "redirect" : "forward"
    target_group_arn = local.redirect_http_to_https ? null : aws_lb_target_group.app.arn

    dynamic "redirect" {
      for_each = local.redirect_http_to_https ? [1] : []
      content {
        protocol    = "HTTPS"
        port        = "443"
        status_code = "HTTP_301"
      }
    }
  }
}
