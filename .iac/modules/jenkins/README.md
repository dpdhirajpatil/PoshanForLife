> **DEPRECATED (2026-09-27):** replaced by GitHub Actions + OIDC (`shared/github-oidc`,
> `modules/github-deploy-role`). Never applied; safe to delete this folder and `shared/jenkins/`.

# modules/jenkins

One EC2 instance (Amazon Linux 2023) running Jenkins, Docker, and the AWS CLI,
with an IAM role attached that lets it push to ECR and deploy to ECS.

**After first apply:**
1. `http://<public_ip>:8080` — Jenkins will ask for an initial admin password.
   Get it via SSM (no need to open SSH):
   ```bash
   aws ssm start-session --target <instance_id>
   sudo cat /var/lib/jenkins/secrets/initialAdminPassword
   ```
2. Install suggested plugins, create your admin user.
3. Add the "Docker Pipeline" and "Amazon ECR" plugins (or use raw `aws`/`docker`
   CLI calls in a Jenkinsfile — no plugin strictly required since the AWS CLI
   and Docker are already on the box).

**Free tier boundaries this module respects:**
- Single `t3.micro`/`t2.micro` instance (750 hrs/month free for 12 months).
- Root volume capped at 30 GB (Free Tier EBS limit).
- Elastic IP is free as long as it stays attached to a running instance —
  don't stop the instance without releasing the EIP, or it starts billing.

**Not yet automated (manual for now):** the initial Jenkins admin setup,
plugin installation, and pipeline job creation. Terraform provisions the
server; configuring Jenkins itself is a one-time manual step (or a future
Ansible/JCasC addition if you want that fully automated too).
