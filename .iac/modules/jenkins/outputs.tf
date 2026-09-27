output "public_ip" {
  value       = aws_eip.jenkins.public_ip
  description = "Stable public IP for Jenkins. Access at http://<this-ip>:8080"
}

output "instance_id" {
  value = aws_instance.jenkins.id
}

output "iam_role_arn" {
  value = aws_iam_role.jenkins.arn
}

output "security_group_id" {
  value = aws_security_group.jenkins.id
}
