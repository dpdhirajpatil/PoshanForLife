############################################
# CloudFront: free HTTPS in front of the ALB, no domain required
############################################
# The ALB itself stays HTTP-only (see alb.tf). CloudFront terminates TLS
# using its own default *.cloudfront.net certificate and forwards to the
# ALB over plain HTTP — that hop isn't a browser connection, so it isn't
# subject to mixed-content rules. This exists so an HTTPS frontend (or any
# HTTPS caller) can reach the API today, without waiting on Route 53 + ACM.
#
# Caching is deliberately OFF (TTL=0, everything forwarded): this fronts a
# dynamic API, not static content, so CloudFront here is acting purely as
# a TLS terminator. When a custom domain exists, add it as an alias + a
# real ACM cert here — nothing about the ALB or ECS service needs to change.
#
# Cost: no free-tier dependency, pay-as-you-go (requests + data transfer).
# Negligible at dev-scale traffic.

resource "aws_cloudfront_distribution" "api" {
  count   = var.enable_https_cdn ? 1 : 0
  enabled = true

  comment             = "${var.app_name} - HTTPS front door for the ALB"
  is_ipv6_enabled     = true
  price_class     = "PriceClass_100" # cheapest edge location set; raise if non-US/EU latency matters

  origin {
    origin_id   = "alb"
    domain_name = aws_lb.app.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # CloudFront -> ALB stays HTTP; the ALB has no cert to validate anyway
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "alb"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    compress                = true

    # No caching: forward everything, let the API decide. min/default/max at
    # 0 means CloudFront treats every response as already-expired.
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0

    forwarded_values {
      query_string = true
      headers      = ["*"] # forwarding "*" implicitly disables caching too, belt-and-braces with the TTLs above

      cookies {
        forward = "all"
      }
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true # *.cloudfront.net cert; swap for ACM + alias once a domain exists
  }

  tags = var.tags
}
