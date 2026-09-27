output "alb_dns_name" {
  value       = aws_lb.app.dns_name
  description = "Point Route 53 (or just curl this directly for now) at this."
}

output "ecr_repository_url" {
  value       = aws_ecr_repository.app.repository_url
  description = "Push images here from GitHub Actions."
}

output "ecr_repository_arn" {
  value = aws_ecr_repository.app.arn
}

output "ecr_repository_name" {
  value = aws_ecr_repository.app.name
}

output "ecs_service_arn" {
  value = aws_ecs_service.app.id # the service's id IS its ARN
}

output "container_name" {
  value = var.app_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.app.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "task_definition_family" {
  value = aws_ecs_task_definition.app.family
}

output "task_execution_role_arn" {
  value = aws_iam_role.task_execution.arn
}

output "app_secret_arn" {
  value       = one(aws_secretsmanager_secret.app[*].arn)
  description = "null when app_secret_keys is empty."
}
