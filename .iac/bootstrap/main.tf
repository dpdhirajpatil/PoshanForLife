############################################
# Bootstrap: Terraform remote state backend
############################################
# Run this ONCE, manually, with local state, before anything else in .iac.
# It creates the S3 bucket that every other module/environment will use
# as its remote backend. Nothing here should change often.
#
# Apply order for the whole .iac tree:
#   1. .iac/bootstrap            (this folder)      -> creates state bucket
#   2. .iac/shared/github-oidc                        -> GitHub Actions OIDC provider
#   3. .iac/environments/dev/backend                   -> ECS+EC2+ALB for backend app
#
# State layout in the bucket: <env>/<component>/terraform.tfstate for
# local/dev/qa/prod, plus shared/<name>/terraform.tfstate for per-account shared configs.
#
# Free tier note: S3 standard storage is free up to 5 GB / 20k GET / 2k PUT
# per month for the first 12 months. Terraform state files are a few KB each,
# so this stays free indefinitely for a project this size.

terraform {
  required_version = ">= 1.7.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  # Intentionally NO backend block here — this bootstrap step itself
  # uses local state, because the S3 bucket it creates doesn't exist yet.
}

provider "aws" {
  region              = var.aws_region
  profile             = var.aws_profile
  allowed_account_ids = length(var.allowed_account_ids) > 0 ? var.allowed_account_ids : null
}

variable "aws_region" {
  description = "AWS region for all Poshan infra"
  type        = string
  default     = "ap-south-1" # Mumbai
}

variable "aws_profile" {
  description = "AWS CLI profile for the account this config deploys into. null = default credential chain. Set per env when qa/prod move to their own AWS accounts."
  type        = string
  default     = null
}

variable "allowed_account_ids" {
  description = "Safety guard: Terraform refuses to run if the credentials resolve to any other account. Empty = no guard."
  type        = list(string)
  default     = []
}

variable "state_bucket_name" {
  description = "Globally-unique S3 bucket name for Terraform remote state"
  type        = string
  default     = "poshan-backend-terraform-state"
}

variable "environments" {
  description = "One top-level folder (key prefix) per environment in the state bucket"
  type        = list(string)
  default     = ["local", "dev", "qa", "prod"]
}

resource "aws_s3_bucket" "terraform_state" {
  bucket = var.state_bucket_name

  # Safety net: prevents `terraform destroy` from ever deleting your state
  # bucket by accident. Remove manually if you genuinely need to tear it down.
  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Project   = "poshan-for-life"
    ManagedBy = "terraform"
    Purpose   = "terraform-remote-state"
  }
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled" # lets you recover a previous state file if something goes wrong
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket                  = aws_s3_bucket.terraform_state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# S3 has no real folders — these zero-byte "<env>/" objects just make each
# environment's prefix visible in the console before any state is written.
# Each config's backend key then lives under its env, e.g. "dev/backend/terraform.tfstate".
resource "aws_s3_object" "environment_folder" {
  for_each = toset(var.environments)

  bucket  = aws_s3_bucket.terraform_state.id
  key     = "${each.value}/"
  content = ""
}

output "state_bucket_name" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "environment_state_prefixes" {
  value = [for o in aws_s3_object.environment_folder : o.key]
}

output "aws_region" {
  value = var.aws_region
}
