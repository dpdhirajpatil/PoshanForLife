############################################
# environments/dev/backend — Poshan backend, dev environment
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
    # bucket = "poshan-backend-terraform-state"   # from .iac/bootstrap output
    # key    = "dev/backend/terraform.tfstate"
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
  description = "Your IP in CIDR form, e.g. 49.207.xxx.xxx/32"
}

variable "key_pair_name" {
  type        = string
  description = "Existing EC2 key pair name for SSH access (one key pair can be shared across environments in the same account)"
}

variable "github_repository" {
  type        = string
  default     = "dpdhirajpatil/PoshanForLife"
  description = "owner/repo whose GitHub Actions may deploy to this environment."
}

module "networking" {
  source = "../../../modules/networking"

  # 2 AZs, not 3: the ALB bills a public IPv4 per enabled AZ (~$3.65/mo each).
  # The ALB needs at least 2; the ECS host's ASG uses the same subnets.
  availability_zones = ["ap-south-1a", "ap-south-1b"]
}

module "backend_app" {
  source = "../../../modules/ecs-ec2-app"

  app_name         = "poshan-backend-dev"
  vpc_id           = module.networking.vpc_id
  subnet_ids       = module.networking.subnet_ids
  ssh_allowed_cidr = var.ssh_allowed_cidr
  key_pair_name    = var.key_pair_name

  container_port    = 8080
  health_check_path = "/api/v1/health"

  # t3.micro registers ~940 MB with ECS. 700 MB leaves room for the OS + agent
  # but NOT for a second copy during a rolling deploy, hence stop-then-start
  # (0% / 100%): ~1-2 min of dev downtime per deploy instead of a stuck rollout.
  task_memory                        = 700
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  # Spring Boot boots in ~60-120s on a t3.micro; don't let the ALB kill it first.
  health_check_grace_period_seconds = 180
  # Stop-then-start deploys wait for draining, so the 300s default = 5 min extra downtime.
  deregistration_delay_seconds = 30

  container_environment = {
    # JVM defaults to 25% of the container limit for heap (~175 MB); 75% ≈ 525 MB.
    JAVA_TOOL_OPTIONS      = "-XX:MaxRAMPercentage=75"
    SPRING_PROFILES_ACTIVE = "dev"
    # Supabase project "poshan-dev". Session pooler (IPv4) on 5432, not the
    # IPv6-only direct host and not the 6543 transaction pooler (breaks
    # Hibernate/Flyway prepared statements).
    DB_URL      = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require"
    DB_USERNAME = "postgres.ddkwclxvlmgvyvsjpyyv"
  }

  # Values live in Secrets Manager "poshan-backend-dev/app", set out-of-band.
  app_secret_keys             = ["DB_PASSWORD", "JWT_SECRET"]
  secret_recovery_window_days = 0

  tags = {
    Project     = "poshan-for-life"
    Environment = "dev"
    Component   = "backend"
    ManagedBy   = "terraform"
  }
}

module "github_deploy" {
  source = "../../../modules/github-deploy-role"

  role_name          = "poshan-backend-dev-github-deploy"
  github_repository  = var.github_repository
  github_environment = "dev"
  # This repo uses GitHub's immutable OIDC subject format.
  github_immutable_ids = {
    owner_id      = 264592782
    repository_id = 1303041314
  }
  ecr_repository_arn = module.backend_app.ecr_repository_arn
  ecs_service_arn    = module.backend_app.ecs_service_arn
  pass_role_arns     = [module.backend_app.task_execution_role_arn]

  tags = {
    Project     = "poshan-for-life"
    Environment = "dev"
    Component   = "backend"
    ManagedBy   = "terraform"
  }
}

output "alb_dns_name" {
  value = module.backend_app.alb_dns_name
}

output "ecr_repository_url" {
  value = module.backend_app.ecr_repository_url
}

output "ecs_cluster_name" {
  value = module.backend_app.ecs_cluster_name
}

output "ecs_service_name" {
  value = module.backend_app.ecs_service_name
}

# Copy these into GitHub -> Settings -> Environments -> "dev" -> Variables.
# None are secrets (the role can only be assumed from this repo's "dev" environment).
output "github_environment_variables" {
  value = {
    AWS_REGION          = var.aws_region
    AWS_DEPLOY_ROLE_ARN = module.github_deploy.role_arn
    ECR_REPOSITORY      = module.backend_app.ecr_repository_name
    ECS_CLUSTER         = module.backend_app.ecs_cluster_name
    ECS_SERVICE         = module.backend_app.ecs_service_name
    ECS_TASK_FAMILY     = module.backend_app.task_definition_family
    CONTAINER_NAME      = module.backend_app.container_name
  }
}

output "app_secret_arn" {
  value = module.backend_app.app_secret_arn
}
