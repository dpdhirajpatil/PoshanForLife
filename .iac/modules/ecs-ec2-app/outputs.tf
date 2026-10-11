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

output "cloudfront_domain_name" {
  value       = one(aws_cloudfront_distribution.api[*].domain_name)
  description = "HTTPS address for the API (null if enable_https_cdn=false). Use this, not alb_dns_name, as the base URL anything HTTPS-only (a browser, a frontend) should call."
}

output "static_ip" {
  value       = one(aws_eip.ecs_instance[*].public_ip)
  description = "Fixed public IP of the ECS host, stable across stop/start cycles (null if enable_static_ip=false). Whitelist this once in Supabase's Network Restrictions instead of re-adding an IP after every restart."
}

output "app_secret_arn" {
  value       = one(aws_secretsmanager_secret.app[*].arn)
  description = "null when app_secret_keys is empty."
}

output "acm_validation_records" {
  value = [
    for o in try(aws_acm_certificate.api[0].domain_validation_options, []) : {
      name  = o.resource_record_name
      type  = o.resource_record_type
      value = o.resource_record_value
    }
  ]
  description = "DNS record(s) to add at the registrar so ACM can issue the certificate (empty if https_domain_name is unset). Keep them: renewal re-checks them."
}

output "acm_certificate_status" {
  value       = try(aws_acm_certificate.api[0].status, null)
  description = "ACM certificate status as of the last apply/refresh (PENDING_VALIDATION until the DNS record is seen, then ISSUED)."
}
