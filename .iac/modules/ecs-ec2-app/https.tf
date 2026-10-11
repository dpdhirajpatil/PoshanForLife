############################################
# HTTPS on the ALB with a free ACM certificate (custom domain)
############################################
# Alternative to CloudFront for HTTPS: a :443 listener on the ALB itself,
# using an ACM certificate for a domain we own (DNS hosted outside AWS, so
# validation is a CNAME added by hand at the registrar).
#
# Two-step rollout, because ACM can't issue until that CNAME exists:
#   1. https_domain_name = "dev.api.example.com"  -> apply. Creates the
#      certificate (PENDING_VALIDATION); copy the acm_validation_records
#      output into the registrar's DNS. Leave it there forever: ACM renews
#      by re-checking it.
#   2. enable_https_listener = true                -> apply. Waits for the
#      certificate to be ISSUED, then adds :443, opens 443 on the ALB SG and
#      turns :80 into a redirect to https.
# Then point the hostname (CNAME) at alb_dns_name.
#
# Cost: ACM public certificates are free when used with an ALB.

resource "aws_acm_certificate" "api" {
  count             = var.https_domain_name != "" ? 1 : 0
  domain_name       = var.https_domain_name
  validation_method = "DNS"

  # A replacement cert must exist before the old one is detached from the
  # listener, or the listener update fails.
  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.tags, { Name = "${var.app_name}-api-cert" })
}

resource "aws_acm_certificate_validation" "api" {
  count           = var.enable_https_listener ? 1 : 0
  certificate_arn = aws_acm_certificate.api[0].arn

  # DNS lives at the registrar, so there's no record for Terraform to create;
  # this only waits (up to the timeout) for ACM to see the CNAME.
  timeouts {
    create = "30m"
  }

  lifecycle {
    precondition {
      condition     = var.https_domain_name != ""
      error_message = "enable_https_listener needs https_domain_name set (and its validation CNAME added to DNS)."
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = var.enable_https_listener ? 1 : 0
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06" # TLS 1.3 + 1.2 only
  certificate_arn   = aws_acm_certificate_validation.api[0].certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
