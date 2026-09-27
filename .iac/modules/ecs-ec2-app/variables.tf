variable "app_name" {
  type        = string
  description = "Short app/environment identifier, e.g. \"poshan-backend-dev\". Used to name/tag everything."
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  type        = list(string)
  description = "Public subnets for both the ALB and the ECS container instance. Must be the SAME set for both: an ALB only routes to targets in AZs it is enabled in."
}

variable "instance_type" {
  type        = string
  default     = "t3.micro"
  description = "Free-tier-eligible instance type for the ECS container host."
}

variable "root_volume_size_gb" {
  type    = number
  default = 30
}

variable "key_pair_name" {
  type        = string
  description = "Existing EC2 key pair name, for emergency SSH access to the container host."
}

variable "ssh_allowed_cidr" {
  type        = string
  description = "CIDR allowed to SSH into the ECS container host. Use YOUR IP, not 0.0.0.0/0."
}

variable "container_port" {
  type        = number
  default     = 8080
  description = "Port the Spring Boot app listens on inside the container."
}

variable "health_check_path" {
  type    = string
  default = "/api/v1/health"
}

variable "task_cpu" {
  type        = number
  default     = 512
  description = "CPU units reserved for the task (out of ~1024 available on a t3.micro's 1 vCPU)."
}

variable "task_memory" {
  type        = number
  default     = 400
  description = "Memory (MB) reserved for the task. Kept well under t3.micro's ~1GB to leave headroom for the OS + ECS agent."
}

variable "container_environment" {
  type        = map(string)
  default     = {}
  description = "Non-secret env vars for the container (e.g. JAVA_TOOL_OPTIONS). Deploys copy the current task definition, so these carry forward to real-image revisions."
}

variable "app_secret_keys" {
  type        = list(string)
  default     = []
  description = "Env var names to inject from ONE Secrets Manager secret, \"<app_name>/app\" (a JSON object with these keys). Terraform creates the empty secret only — values are set out-of-band (aws secretsmanager put-secret-value) so they never land in Terraform state."
}

variable "secret_recovery_window_days" {
  type        = number
  default     = 7
  description = "Days a deleted app secret stays recoverable (0 = delete immediately, lets the same name be recreated right away)."
}

variable "health_check_grace_period_seconds" {
  type        = number
  default     = 0
  description = "Seconds ECS ignores failing ALB health checks after a task starts. Spring Boot on a t3.micro needs ~60-120s to boot."
}

variable "deregistration_delay_seconds" {
  type        = number
  default     = 300
  description = "How long the ALB drains a target before removal. Every stop and every stop-then-start deploy waits this long."
}

variable "deployment_minimum_healthy_percent" {
  type        = number
  default     = 100
  description = "Set 0 for single-instance envs where two copies of the task don't fit on the host: ECS then stops the old task before starting the new one (brief downtime)."
}

variable "deployment_maximum_percent" {
  type    = number
  default = 200
}

variable "placeholder_image" {
  type        = string
  default     = "public.ecr.aws/nginx/nginx:latest"
  description = "Image used for the FIRST task definition revision, before the first GitHub Actions deploy. Lets you validate the ECS/ALB wiring independently of the real app. The deploy workflow registers new revisions with the real image afterward."
}

variable "tags" {
  type    = map(string)
  default = {}
}
