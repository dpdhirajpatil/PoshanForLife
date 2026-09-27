variable "vpc_id" {
  type        = string
  description = "VPC to launch Jenkins in"
}

variable "subnet_id" {
  type        = string
  description = "Public subnet to launch the Jenkins instance in"
}

variable "instance_type" {
  type        = string
  default     = "t3.micro"
  description = "Free-tier-eligible instance type. t2.micro is the classic free-tier type; t3.micro is also free-tier-eligible in most accounts/regions as of 2024 — verify in the AWS Billing console's Free Tier page after first apply. If in doubt, use t2.micro."
}

variable "ssh_allowed_cidr" {
  type        = string
  description = "CIDR allowed to SSH into Jenkins (port 22). Set this to YOUR IP (e.g. 49.x.x.x/32), never 0.0.0.0/0."
}

variable "jenkins_ui_allowed_cidr" {
  type        = string
  default     = "0.0.0.0/0"
  description = "CIDR allowed to reach the Jenkins web UI (port 8080). Defaulted open since you'll want to access it as an admin from anywhere; tighten to your IP if you prefer, or put it behind a VPN later."
}

variable "root_volume_size_gb" {
  type        = number
  default     = 30
  description = "EBS root volume size. 30 GB is the max covered by Free Tier (General Purpose SSD)."
}

variable "key_pair_name" {
  type        = string
  description = "Name of an EXISTING EC2 key pair (create via 'aws ec2 create-key-pair' or the console) used for SSH access."
}

variable "tags" {
  type    = map(string)
  default = {}
}
