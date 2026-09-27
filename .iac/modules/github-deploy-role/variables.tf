variable "role_name" {
  type        = string
  description = "IAM role name, e.g. \"poshan-backend-dev-github-deploy\"."
}

variable "github_repository" {
  type        = string
  description = "owner/repo allowed to assume this role, e.g. \"dpdhirajpatil/PoshanForLife\"."
}

variable "github_environment" {
  type        = string
  description = "GitHub Environment name the workflow job must declare (environment: <this>), e.g. \"dev\"."
}

variable "ecr_repository_arn" {
  type = string
}

variable "ecs_service_arn" {
  type = string
}

variable "pass_role_arns" {
  type        = list(string)
  description = "Roles the new task definition references (execution role, task role if any)."
}

variable "tags" {
  type    = map(string)
  default = {}
}
