############################################
# shared/security-baseline — account-wide security controls, one per AWS account
############################################
# Things that protect the whole account rather than one environment:
#   - CloudTrail: an audit log of every AWS API call (who changed what, when),
#     multi-region, with log-file integrity validation, in a private bucket.
#   - GuardDuty: threat detection on CloudTrail/VPC flow/DNS (optional flag:
#     the account currently gets SubscriptionRequiredException, like CloudFront).
#   - EBS encryption by default for this region, so no unencrypted volume can
#     be created by anything (not only the launch templates in .iac).
#   - Account-level S3 Block Public Access: no bucket can be made public.
#   - IAM Access Analyzer: flags any resource policy shared outside the account.
#   - IAM password policy for console users.
#   - Optional monthly budget alert by email.
#
# Apply after bootstrap, before or after the environments (nothing depends on
# it). If qa/prod move to separate AWS accounts, apply once per account.
#
# Cost: CloudTrail's first management-event trail is free (S3 storage only,
# cents/month). Access Analyzer (external access), EBS default encryption,
# Block Public Access and the password policy are free. GuardDuty: 30-day
# trial, then usage-based (a few $/month for an account this size).
# Budgets: first two are free.

terraform {
  required_version = ">= 1.7.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    # bucket = "poshan-backend-terraform-state"   # from .iac/bootstrap output
    # key    = "shared/security-baseline/terraform.tfstate"
    # region = "ap-south-1"
  }
}

provider "aws" {
  region              = var.aws_region
  profile             = var.aws_profile
  allowed_account_ids = length(var.allowed_account_ids) > 0 ? var.allowed_account_ids : null
}

variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "aws_profile" {
  description = "AWS CLI profile for the account this config deploys into. null = default credential chain."
  type        = string
  default     = null
}

variable "allowed_account_ids" {
  description = "Safety guard: Terraform refuses to run if the credentials resolve to any other account. Empty = no guard."
  type        = list(string)
  default     = []
}

variable "cloudtrail_retention_days" {
  type        = number
  default     = 365
  description = "Days CloudTrail log files are kept in S3 before expiring."
}

variable "enable_guardduty" {
  type        = bool
  default     = false
  description = "Turn on GuardDuty. Off until the account can subscribe (ListDetectors currently returns SubscriptionRequiredException — same account-verification gate as CloudFront)."
}

variable "budget_alert_email" {
  type        = string
  default     = null
  description = "Email for the monthly cost budget alerts (80% forecast, 100% actual). null = no budget."
}

variable "monthly_budget_usd" {
  type    = number
  default = 25
}

locals {
  tags = {
    Project     = "poshan-for-life"
    Environment = "shared"
    ManagedBy   = "terraform"
  }
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

############################################
# CloudTrail
############################################

resource "aws_s3_bucket" "cloudtrail" {
  bucket = "poshan-cloudtrail-${data.aws_caller_identity.current.account_id}"

  # Audit logs: never let a `terraform destroy` take them with it.
  lifecycle {
    prevent_destroy = true
  }

  tags = local.tags
}

resource "aws_s3_bucket_public_access_block" "cloudtrail" {
  bucket                  = aws_s3_bucket.cloudtrail.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_versioning" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id
  versioning_configuration {
    status = "Enabled" # an overwritten/deleted log file stays recoverable
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id

  rule {
    id     = "expire"
    status = "Enabled"
    filter {}
    expiration {
      days = var.cloudtrail_retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

locals {
  trail_name = "poshan-account-trail"
  trail_arn  = "arn:${data.aws_partition.current.partition}:cloudtrail:${var.aws_region}:${data.aws_caller_identity.current.account_id}:trail/${local.trail_name}"
}

resource "aws_s3_bucket_policy" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "CloudTrailAclCheck"
        Effect    = "Allow"
        Principal = { Service = "cloudtrail.amazonaws.com" }
        Action    = "s3:GetBucketAcl"
        Resource  = aws_s3_bucket.cloudtrail.arn
        Condition = { StringEquals = { "aws:SourceArn" = local.trail_arn } }
      },
      {
        Sid       = "CloudTrailWrite"
        Effect    = "Allow"
        Principal = { Service = "cloudtrail.amazonaws.com" }
        Action    = "s3:PutObject"
        Resource  = "${aws_s3_bucket.cloudtrail.arn}/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
        Condition = { StringEquals = { "aws:SourceArn" = local.trail_arn } }
      },
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = [aws_s3_bucket.cloudtrail.arn, "${aws_s3_bucket.cloudtrail.arn}/*"]
        Condition = { Bool = { "aws:SecureTransport" = "false" } }
      },
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.cloudtrail]
}

resource "aws_cloudtrail" "account" {
  name                          = local.trail_name
  s3_bucket_name                = aws_s3_bucket.cloudtrail.id
  is_multi_region_trail         = true # an attacker's favourite trick is working in a region nobody watches
  include_global_service_events = true # IAM, STS, CloudFront
  enable_log_file_validation    = true # signed digests: tampering with a log file is detectable

  tags = local.tags

  depends_on = [aws_s3_bucket_policy.cloudtrail]
}

############################################
# GuardDuty
############################################

resource "aws_guardduty_detector" "main" {
  count  = var.enable_guardduty ? 1 : 0
  enable = true

  tags = local.tags
}

############################################
# Account-wide guardrails
############################################

resource "aws_ebs_encryption_by_default" "main" {
  enabled = true
}

resource "aws_s3_account_public_access_block" "main" {
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_accessanalyzer_analyzer" "account" {
  analyzer_name = "poshan-account-analyzer"
  type          = "ACCOUNT"

  tags = local.tags
}

resource "aws_iam_account_password_policy" "main" {
  minimum_password_length        = 14
  require_lowercase_characters   = true
  require_uppercase_characters   = true
  require_numbers                = true
  require_symbols                = true
  allow_users_to_change_password = true
  password_reuse_prevention      = 24
}

############################################
# Cost alert (optional): also the cheapest intrusion alarm — crypto-miners
# on stolen credentials show up on the bill first.
############################################

resource "aws_budgets_budget" "monthly" {
  count        = var.budget_alert_email != null ? 1 : 0
  name         = "poshan-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_alert_email]
  }
}

output "cloudtrail_bucket" {
  value = aws_s3_bucket.cloudtrail.bucket
}

output "guardduty_detector_id" {
  value = one(aws_guardduty_detector.main[*].id)
}
