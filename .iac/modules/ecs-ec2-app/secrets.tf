############################################
# Secrets Manager: one JSON secret of app credentials per environment
############################################
# Terraform owns the secret's existence, never its value: set the value with
#   aws secretsmanager put-secret-value --secret-id <app_name>/app --secret-string file://...
# so credentials stay out of Terraform state and plan output.
# The task execution role can already read "<app_name>/*" (iam.tf).
# Cost: ~$0.40/month per secret.

resource "aws_secretsmanager_secret" "app" {
  count = length(var.app_secret_keys) > 0 ? 1 : 0

  name                    = "${var.app_name}/app"
  description             = "Runtime credentials for ${var.app_name}: JSON object with keys ${join(", ", var.app_secret_keys)}"
  recovery_window_in_days = var.secret_recovery_window_days

  tags = var.tags
}
