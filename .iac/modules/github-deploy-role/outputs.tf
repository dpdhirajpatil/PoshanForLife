output "role_arn" {
  value       = aws_iam_role.deploy.arn
  description = "Set as the GitHub Environment variable AWS_DEPLOY_ROLE_ARN."
}
