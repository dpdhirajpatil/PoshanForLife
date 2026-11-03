############################################
# ALB access logs: private S3 bucket (optional, enable_alb_access_logs)
############################################
# One line per request (client IP, path, status, TLS details), for working
# out after the fact who hit what. ALB log delivery only supports SSE-S3
# (AES256) on the target bucket, not KMS.
#
# Cost: S3 storage only (a few cents/month at dev traffic); objects expire
# after alb_access_log_retention_days.

data "aws_caller_identity" "current" {}

# The regional ELB account that writes the logs (ap-south-1 predates the
# log-delivery service principal, so the account form is required there).
data "aws_elb_service_account" "current" {
  count = var.enable_alb_access_logs ? 1 : 0
}

resource "aws_s3_bucket" "alb_logs" {
  count  = var.enable_alb_access_logs ? 1 : 0
  bucket = "${var.app_name}-alb-logs-${data.aws_caller_identity.current.account_id}"

  # Logs are disposable; let `terraform destroy` empty the bucket.
  force_destroy = true

  tags = merge(var.tags, { Name = "${var.app_name}-alb-logs" })
}

resource "aws_s3_bucket_public_access_block" "alb_logs" {
  count                   = var.enable_alb_access_logs ? 1 : 0
  bucket                  = aws_s3_bucket.alb_logs[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "alb_logs" {
  count  = var.enable_alb_access_logs ? 1 : 0
  bucket = aws_s3_bucket.alb_logs[0].id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_ownership_controls" "alb_logs" {
  count  = var.enable_alb_access_logs ? 1 : 0
  bucket = aws_s3_bucket.alb_logs[0].id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "alb_logs" {
  count  = var.enable_alb_access_logs ? 1 : 0
  bucket = aws_s3_bucket.alb_logs[0].id

  rule {
    id     = "expire"
    status = "Enabled"
    filter {}
    expiration {
      days = var.alb_access_log_retention_days
    }
  }
}

resource "aws_s3_bucket_policy" "alb_logs" {
  count  = var.enable_alb_access_logs ? 1 : 0
  bucket = aws_s3_bucket.alb_logs[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AlbLogDelivery"
        Effect    = "Allow"
        Principal = { AWS = data.aws_elb_service_account.current[0].arn }
        Action    = "s3:PutObject"
        Resource  = "${aws_s3_bucket.alb_logs[0].arn}/alb/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
      },
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = [aws_s3_bucket.alb_logs[0].arn, "${aws_s3_bucket.alb_logs[0].arn}/*"]
        Condition = { Bool = { "aws:SecureTransport" = "false" } }
      },
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.alb_logs]
}
