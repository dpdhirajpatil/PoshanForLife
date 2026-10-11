variable "app_name" {
  type        = string
  description = "Name for the ECR repo, log group, task family, target group and service (e.g. poshan-frontend-dev)."
}

variable "host_name" {
  type        = string
  description = "Hostname the ALB routes to this app (e.g. dev.ui.poshanforlife.com). Its DNS CNAME must point at the ALB."
}

# --- Shared platform (outputs of the backend environment) ---

variable "vpc_id" {
  type = string
}

variable "ecs_cluster_arn" {
  type = string
}

variable "capacity_provider_name" {
  type = string
}

variable "task_execution_role_arn" {
  type        = string
  description = "Shared execution role: lets ECS pull from ECR and write logs."
}

variable "listener_arn" {
  type        = string
  description = "ALB listener to add the host rule to: :443 once HTTPS is on, else :80 (the backend's public_listener_arn output)."
}

variable "https_listener_arn" {
  type        = string
  default     = null
  description = "Shared :443 listener, for attaching this app's certificate. null until the backend's HTTPS listener exists."
}

variable "listener_rule_priority" {
  type        = number
  default     = 100
  description = "Unique per listener; lower is evaluated first. Give each app on the ALB its own."
}

# --- Task ---

variable "container_port" {
  type    = number
  default = 80
}

variable "task_memory" {
  type        = number
  default     = 64
  description = "MiB. nginx serving static files idles at a few MiB; 64 leaves headroom next to the 700 MiB API on a t3.micro."
}

variable "health_check_path" {
  type        = string
  default     = "/"
  description = "Both the placeholder nginx image and the built SPA answer 200 here, so the target is healthy from the first apply."
}

variable "placeholder_image" {
  type        = string
  default     = "public.ecr.aws/nginx/nginx:stable-alpine"
  description = "Image for the first task definition only; GitHub Actions replaces it on the first deploy."
}

variable "log_retention_days" {
  type    = number
  default = 14
}

# --- HTTPS certificate (two-step, see main.tf) ---

variable "request_certificate" {
  type        = bool
  default     = false
  description = "Step 1: request an ACM certificate for host_name; add the acm_validation_records output to DNS."
}

variable "attach_certificate" {
  type        = bool
  default     = false
  description = "Step 2: wait for the certificate to be ISSUED and attach it to the shared :443 listener (SNI)."
}

variable "tags" {
  type    = map(string)
  default = {}
}
