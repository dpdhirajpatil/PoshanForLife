output "ecr_repository_arn" {
  value = aws_ecr_repository.app.arn
}

output "ecr_repository_name" {
  value = aws_ecr_repository.app.name
}

output "ecs_service_arn" {
  value = aws_ecs_service.app.id
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "task_definition_family" {
  value = aws_ecs_task_definition.app.family
}

output "container_name" {
  value = var.app_name
}

output "acm_validation_records" {
  value = [
    for o in try(aws_acm_certificate.app[0].domain_validation_options, []) : {
      name  = o.resource_record_name
      type  = o.resource_record_type
      value = o.resource_record_value
    }
  ]
  description = "DNS record(s) to add so ACM can issue this app's certificate. Keep them for renewals."
}

output "acm_certificate_status" {
  value = try(aws_acm_certificate.app[0].status, null)
}
