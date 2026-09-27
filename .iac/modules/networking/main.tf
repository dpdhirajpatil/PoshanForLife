############################################
# Module: networking
############################################
# Deliberately minimal: we reuse the AWS account's DEFAULT VPC instead of
# creating a custom one. A custom VPC needs NAT Gateways for private-subnet
# internet access, and NAT Gateways have NO free tier (~$32/month + data
# processing) — not worth it for a single-instance, low-traffic setup.
#
# The default VPC's subnets are all public (have an Internet Gateway route),
# which is fine here: our EC2 instances/ALB are meant to be reachable, and
# access is controlled by security groups, not network topology.

variable "availability_zones" {
  type        = list(string)
  default     = []
  description = "Limit to the default subnets in these AZs. Empty = all AZs. Fewer AZs = fewer ALB nodes = fewer billed public IPv4 addresses (~$3.65/mo each)."
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "default-for-az"
    values = ["true"]
  }

  dynamic "filter" {
    for_each = length(var.availability_zones) > 0 ? [1] : []
    content {
      name   = "availability-zone"
      values = var.availability_zones
    }
  }
}

output "vpc_id" {
  value = data.aws_vpc.default.id
}

output "subnet_ids" {
  value = data.aws_subnets.default.ids
}
