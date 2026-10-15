############################################
# dev frontend: the Angular staff portal on the dev backend's ECS cluster
############################################
# Separate state from environments/dev/backend, so a frontend apply can never
# change the API's infrastructure. Everything shared (cluster, capacity,
# ALB listener, execution role) is read from the backend's state.
#
# Apply order: environments/dev/backend first (it owns the cluster and ALB).
# Re-apply this config whenever the backend's HTTPS listener is switched on,
# so the host rule moves from :80 to :443.

terraform {
  required_version = ">= 1.7.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    # bucket = "poshan-backend-terraform-state"
    # key    = "dev/frontend/terraform.tfstate"
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
  description = "AWS CLI profile for this env's account. null = default credential chain."
  type        = string
  default     = null
}

variable "allowed_account_ids" {
  description = "Safety guard: Terraform refuses to run if the credentials resolve to any other account. Empty = no guard."
  type        = list(string)
  default     = []
}

variable "github_repository" {
  type        = string
  default     = "dpdhirajpatil/PoshanForLife"
  description = "owner/repo whose GitHub Actions may deploy to this environment."
}

data "terraform_remote_state" "backend" {
  backend = "s3"
  config = {
    bucket  = "poshan-backend-terraform-state"
    key     = "dev/backend/terraform.tfstate"
    region  = var.aws_region
    profile = var.aws_profile
  }
}

locals {
  platform = data.terraform_remote_state.backend.outputs.shared_platform
  tags = {
    Project     = "poshan-for-life"
    Environment = "dev"
    Component   = "frontend"
    ManagedBy   = "terraform"
  }
}

module "portal" {
  source = "../../../modules/ecs-static-site"

  app_name  = "poshan-frontend-dev"
  host_name = "dev.portal.poshanforlife.com"

  vpc_id                  = local.platform.vpc_id
  ecs_cluster_arn         = local.platform.ecs_cluster_arn
  capacity_provider_name  = local.platform.capacity_provider_name
  task_execution_role_arn = local.platform.task_execution_role_arn
  listener_arn            = local.platform.public_listener_arn
  https_listener_arn      = local.platform.https_listener_arn
  listener_rule_priority  = 100

  # dev is normally stopped; env-power.sh start scales this to 1 with the API.
  initial_desired_count = 0

  # HTTPS for dev.portal (DNS at Squarespace, validation CNAME added by hand).
  # Step 1: request. Step 2 (after the CNAME is live AND the backend's
  # enable_https_listener is applied): attach to the :443 listener.
  request_certificate = true
  attach_certificate  = true

  tags = local.tags
}

module "github_deploy" {
  source = "../../../modules/github-deploy-role"

  role_name          = "poshan-frontend-dev-github-deploy"
  github_repository  = var.github_repository
  github_environment = "dev"
  github_immutable_ids = {
    owner_id      = 264592782
    repository_id = 1303041314
  }
  ecr_repository_arn = module.portal.ecr_repository_arn
  ecs_service_arn    = module.portal.ecs_service_arn
  pass_role_arns     = [local.platform.task_execution_role_arn]

  tags = local.tags
}

output "portal_url" {
  value = "https://dev.portal.poshanforlife.com"
}

output "dns_records_to_add" {
  description = "Add at Squarespace: the portal hostname, plus ACM's validation record(s). Keep the validation records for renewals."
  value = concat(
    [{ name = "dev.portal", type = "CNAME", value = local.platform.alb_dns_name }],
    module.portal.acm_validation_records,
  )
}

output "acm_certificate_status" {
  value = module.portal.acm_certificate_status
}

# Copy into GitHub -> Settings -> Environments -> dev -> Variables. Prefixed
# FRONTEND_ so they sit next to the backend's variables in the same
# environment without clashing.
output "github_environment_variables" {
  value = {
    FRONTEND_AWS_DEPLOY_ROLE_ARN = module.github_deploy.role_arn
    FRONTEND_ECR_REPOSITORY      = module.portal.ecr_repository_name
    FRONTEND_ECS_CLUSTER         = local.platform.ecs_cluster_name
    FRONTEND_ECS_SERVICE         = module.portal.ecs_service_name
    FRONTEND_ECS_TASK_FAMILY     = module.portal.task_definition_family
    FRONTEND_CONTAINER_NAME      = module.portal.container_name
  }
}
