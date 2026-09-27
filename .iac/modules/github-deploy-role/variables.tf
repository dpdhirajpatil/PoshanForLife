variable "role_name" {
  type        = string
  description = "IAM role name, e.g. \"poshan-backend-dev-github-deploy\"."
}

variable "github_repository" {
  type        = string
  description = "owner/repo allowed to assume this role, e.g. \"dpdhirajpatil/PoshanForLife\"."
}

variable "github_immutable_ids" {
  type = object({
    owner_id      = number
    repository_id = number
  })
  default     = null
  description = "Set when the repo uses GitHub's immutable OIDC subject (gh api repos/<o>/<r>/actions/oidc/customization/sub shows use_immutable_subject=true). The sub claim is then repo:<owner>@<owner_id>/<repo>@<repo_id>:... — which also can't be hijacked by someone recreating a deleted repo of the same name."
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
