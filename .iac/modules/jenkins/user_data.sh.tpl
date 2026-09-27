#!/bin/bash
set -euxo pipefail

# ---- System update ----
dnf update -y || yum update -y

# ---- Docker (Jenkins will use this to build/push backend images) ----
dnf install -y docker || yum install -y docker
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

# ---- Java 21 (required by modern Jenkins) ----
dnf install -y java-21-amazon-corretto || yum install -y java-21-amazon-corretto

# ---- Jenkins repo + install ----
# Jenkins moved its RPM repo from redhat-stable/ to rpm-stable/; the key
# below is the gpgkey the repo file itself points at.
curl -fsSL https://pkg.jenkins.io/rpm-stable/jenkins.repo -o /etc/yum.repos.d/jenkins.repo
rpm --import https://pkg.jenkins.io/rpm-stable/repodata/repomd.xml.key
dnf install -y fontconfig jenkins || yum install -y fontconfig jenkins

# Let the jenkins user drive Docker (build/push images from pipelines)
usermod -aG docker jenkins

systemctl enable jenkins
systemctl start jenkins

# ---- AWS CLI v2 (Jenkins pipelines will call this for ECR login / ECS deploy) ----
dnf install -y unzip || yum install -y unzip
curl -s "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip
cd /tmp && unzip -q awscliv2.zip && ./aws/install

echo "Jenkins bootstrap complete." > /var/log/poshan-jenkins-bootstrap.log
