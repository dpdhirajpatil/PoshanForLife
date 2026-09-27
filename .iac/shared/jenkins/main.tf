############################################
# shared/jenkins — the ONE Jenkins server for all environments
############################################

terraform {
  required_version = ">= 1.7.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    # Fill these in via `terraform init -backend-config=...` or edit directly.
    # bucket = "poshan-backend-terraform-state"   # from .iac/bootstrap output
    # key    = "shared/jenkins/terraform.tfstate"
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

variable "ssh_allowed_cidr" {
  type        = string
  description = "Your IP in CIDR form, e.g. 49.207.xxx.xxx/32. Find it with: curl ifconfig.me"
}

variable "key_pair_name" {
  type        = string
  description = "Existing EC2 key pair name for SSH access"
}

module "networking" {
  source = "../../modules/networking"
}

module "jenkins" {
  source = "../../modules/jenkins"

  vpc_id           = module.networking.vpc_id
  subnet_id        = module.networking.subnet_ids[0]
  ssh_allowed_cidr = var.ssh_allowed_cidr
  key_pair_name    = var.key_pair_name

  tags = {
    Project     = "poshan-for-life"
    Environment = "shared"
    ManagedBy   = "terraform"
  }
}

output "jenkins_url" {
  value = "http://${module.jenkins.public_ip}:8080"
}

output "jenkins_iam_role_arn" {
  value = module.jenkins.iam_role_arn
}
