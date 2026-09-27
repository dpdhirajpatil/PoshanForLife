# environments/qa/backend — placeholder

Not built yet. When you're ready for a QA environment, copy the pattern from
`environments/dev/backend/`:

1. Copy `main.tf`, changing `app_name` to something like `poshan-backend-qa`
   and the backend `key` to `qa/backend/terraform.tfstate`.
   If QA gets its own AWS account, set `aws_profile` + `allowed_account_ids`
   in its tfvars and point `backend-config.hcl` at that account's state bucket
   (see "Multi-account" in `.iac/README.md`).
   Keep the `github_deploy` module call with `github_environment = "qa"` and
   create a `qa` GitHub Environment + workflow (copy of the dev one).
2. Copy `terraform.tfvars.example` -> `terraform.tfvars`, fill in real values.
3. `terraform init -backend-config=backend-config.hcl`, then `terraform apply`.

Because all the real logic lives in `modules/ecs-ec2-app`, this environment
automatically gets any fixes/improvements made there — there's nothing to
duplicate except sizing and naming.
