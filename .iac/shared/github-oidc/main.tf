############################################
# shared/github-oidc — GitHub Actions identity provider, one per AWS account
############################################
# Lets GitHub Actions workflows exchange their short-lived OIDC token for
# temporary AWS credentials, so no AWS access keys are ever stored in GitHub.
# Each environment then creates its own narrowly-scoped deploy role that
# trusts this provider (modules/github-deploy-role).
#
# An account can hold only ONE provider for a given URL, so this lives
# outside environments/. If qa/prod move to separate AWS accounts, apply this
# once per account (with that account's aws_profile + state bucket).
#
# Cost: IAM is free.

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
    # key    = "shared/github-oidc/terraform.tfstate"
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
  description = "AWS CLI profile for the account this config deploys into. null = default credential chain. Set per env when qa/prod move to their own AWS accounts."
  type        = string
  default     = null
}

variable "allowed_account_ids" {
  description = "Safety guard: Terraform refuses to run if the credentials resolve to any other account. Empty = no guard."
  type        = list(string)
  default     = []
}

# AWS validates GitHub's OIDC certificate against its own trusted CA store,
# so no thumbprint pinning is needed (or used) for this provider.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  tags = {
    Project     = "poshan-for-life"
    Environment = "shared"
    ManagedBy   = "terraform"
  }
}

output "github_oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}
