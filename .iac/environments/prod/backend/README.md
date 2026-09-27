# environments/prod/backend — placeholder

Not built yet. Same pattern as `environments/qa/backend/README.md`, but
before promoting to prod, revisit the free-tier assumptions baked into
`modules/ecs-ec2-app` — a production environment may
warrant a larger instance type, multi-AZ capacity, HTTPS via ACM, and
tighter security group rules than the dev defaults.

Prod may live in a separate AWS account — see "Multi-account" in
`.iac/README.md` for the `aws_profile` / `allowed_account_ids` / per-account
state bucket setup.

Deploys: add a `github_deploy` module call with `github_environment = "prod"`,
create a `prod` GitHub Environment with **required reviewers**, and copy
`.github/workflows/backend-deploy-dev.yml` as a prod workflow (trigger on
`main` / release tags, `environment: prod`).
