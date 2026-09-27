# Poshan for Life — Infrastructure as Code

Terraform for AWS infra, built free-tier-first for a first-time AWS account.
Region: **ap-south-1 (Mumbai)** everywhere.

**Deploying or operating dev?** See [DEPLOYMENT.md](DEPLOYMENT.md) — the
step-by-step runbook (Supabase DB, secrets, GitHub Actions, start/stop,
rollback, troubleshooting). This README covers structure, design and cost.

## Structure

```
.iac/
├── bootstrap/                  # one-time per account: creates the S3 state bucket
├── modules/                    # reusable, no environment-specific values
│   ├── networking/              # default VPC lookup (optionally limited to chosen AZs)
│   ├── ecs-ec2-app/             # ECR + ECS(EC2) + ALB + task def, reusable per app/env
│   ├── github-deploy-role/      # per-env IAM role GitHub Actions assumes via OIDC
│   └── jenkins/                 # DEPRECATED — replaced by GitHub Actions, safe to delete
├── shared/
│   ├── github-oidc/             # GitHub OIDC identity provider, one per AWS account
│   └── jenkins/                 # DEPRECATED — never applied, safe to delete
└── environments/
    ├── dev/backend/             # calls modules/ecs-ec2-app + github-deploy-role
    ├── dev/frontend/            # placeholder — built in a later phase
    ├── qa/{backend,frontend}/   # placeholders — copy dev's pattern when ready
    └── prod/{backend,frontend}/ # placeholders
```

CI/CD is GitHub Actions (`.github/workflows/backend-deploy-dev.yml`), not a
self-hosted CI server: no EC2 instance, Elastic IP or disk to pay for, and no
long-lived AWS keys in GitHub (OIDC short-lived credentials instead).

## Why this shape

- **`modules/` vs `environments/`:** modules hold reusable logic with no
  knowledge of "dev" vs "qa" vs "prod"; environments just call a module with
  different variables/sizing. Fix a bug or add a feature once in a module,
  every environment that uses it benefits — no copy-pasted drift.
- **One ECS cluster per environment** (`poshan-backend-<env>-cluster`), each
  with its own ALB, EC2 host, ECR repo and deploy role — full isolation, and
  it keeps a later move of qa/prod into separate AWS accounts a config change.
- **`shared/github-oidc` is per account, not per environment:** AWS allows one
  GitHub OIDC provider per account, so it lives outside `environments/`. Each
  environment's deploy role trusts it but only for its own GitHub Environment.
- **Only `dev` is built for real right now.** `qa`/`prod` are placeholders
  with instructions, so we're not paying for 3x the infrastructure before
  `dev` is even validated.

## Apply order (first time)

1. **`bootstrap/`** — creates the S3 bucket all remote state lives in.
   Uses local state (chicken-and-egg: the bucket doesn't exist yet for
   anything to use as a backend).
   ```bash
   cd bootstrap
   terraform init
   terraform apply -var='allowed_account_ids=["046369253799"]'
   ```

2. **`shared/github-oidc/`** — lets GitHub Actions authenticate to AWS.
   ```bash
   cd shared/github-oidc
   cp backend-config.hcl.example backend-config.hcl
   cp terraform.tfvars.example terraform.tfvars
   terraform init -backend-config=backend-config.hcl
   terraform apply
   ```

3. **`environments/dev/backend/`** — ECS+EC2+ALB for the backend app, plus
   the dev GitHub deploy role.
   ```bash
   cd environments/dev/backend
   cp backend-config.hcl.example backend-config.hcl
   cp terraform.tfvars.example terraform.tfvars         # fill in your IP + key pair
   terraform init -backend-config=backend-config.hcl
   terraform apply
   ```

4. **GitHub, one time:** Settings → Environments → create `dev` → add each
   key/value from step 3's `github_environment_variables` output as an
   environment **variable** (none are secrets). From then on, every push to
   `develop` touching `backend/**` (or a manual "Run workflow") builds the
   image, pushes it to ECR, registers a new task definition revision and
   rolls the ECS service. Until the variables exist the workflow skips its
   deploy job instead of failing.

## Prerequisites (once, manual — per the attached deployment guide, Parts 1-3)

- AWS account created, MFA on root, billing budget alert set.
- An IAM user (not root) with credentials configured via `aws configure`.
- An **existing EC2 key pair** in `ap-south-1` (create via
  `aws ec2 create-key-pair --key-name poshan-key --region ap-south-1
  --query 'KeyMaterial' --output text > poshan-key.pem && chmod 400 poshan-key.pem`)
  — needed by `environments/<env>/backend` for emergency SSH to the ECS host.
- Terraform >= 1.7 installed locally.

## Cost posture — AWS Free plan (credit-based)

This account is on AWS's **Free plan** (accounts created after 15 Jul 2025):
there's no "750 free hours for 12 months" — usage draws down a **credit
balance** (US$119.57 with 146 days left as of 2026-09-27, plan ends
2027-02-18). When credits run out or the plan period ends, the account is
closed unless upgraded to a paid plan — upgrade before either happens if dev
must stay up. Approximate ap-south-1 on-demand prices; verify in the AWS
Pricing Calculator / Billing console.

| Resource (dev) | ~USD/month | Notes |
|---|---|---|
| EC2 t3.micro (ECS host) | ~8 | One instance, 24/7 |
| ALB | ~18–20 | Hourly + minimal LCU; the biggest line item |
| Public IPv4 (ECS host + ALB in 2 AZs = 3) | ~11 | $0.005/hr each, attached or not; 2 AZs instead of 3 saves ~$3.65 |
| EBS gp3 30 GB (ECS host root) | ~2.7 | |
| ECR, CloudWatch Logs (14-day retention), S3 state | ~1 | ECR lifecycle keeps last 10 images |
| GitHub Actions | 0 | Within GitHub's free minutes |
| ECS control plane, IAM, OIDC provider | 0 | Always free |
| **Total** | **~$40–43** | ≈ 3 months of the current credit |
| Route 53 hosted zone (future) | ~0.50 | Plus domain registration |
| NAT Gateway (only if moving to Fargate + private subnets) | ~32 + data | Avoided by using the default VPC's public subnets |

Biggest remaining lever: dropping the dev ALB (hit the ECS host's public IP
directly) would save ~$25/month. Set an AWS Budget alert as the backstop.

## Stopping an environment to save credits

`.iac/SKILLS/poshan-env-power/scripts/env-power.sh <start|stop|status> <dev|qa|prod>`
scales that env's ECS service and EC2 host to 0 and back (~$14/mo saved per
stopped env; the ALB keeps billing). Terraform ignores those counts, so
`apply` never undoes a stop. See `.iac/SKILLS/poshan-env-power/SKILL.md`.

## Multi-account (future: separate AWS accounts for qa / prod)

Today everything runs in one account (`046369253799`). The code is ready for
qa and/or prod to move to their own accounts without module changes:

- **No account IDs are hardcoded** in any module; IAM ARNs are AWS-managed or
  wildcarded, and every resource is created in whatever account the provider
  credentials resolve to.
- **Every root config takes `aws_profile` and `allowed_account_ids`.** Point an
  env at its account by setting both in that env's `terraform.tfvars`:
  ```hcl
  aws_profile         = "poshan-prod"        # a profile in ~/.aws/config
  allowed_account_ids = ["<prod-account-id>"] # Terraform aborts on any other account
  ```
  Keep `allowed_account_ids` set even in single-account use — it's what stops a
  prod apply from ever landing in the dev account (or vice versa).
- **State per account:** S3 bucket names are global, so a new account gets its own
  bucket, e.g. `poshan-backend-terraform-state-prod`. Bootstrap it with a local
  workspace so each account's bootstrap state stays separate:
  ```bash
  cd bootstrap
  terraform workspace new prod   # then also apply shared/github-oidc in that account
  terraform apply -var='aws_profile=poshan-prod' \
                  -var='allowed_account_ids=["<prod-account-id>"]' \
                  -var='state_bucket_name=poshan-backend-terraform-state-prod' \
                  -var='environments=["prod"]'
  ```
  Then that env's `backend-config.hcl` uses the new `bucket` plus `profile`.
- **CI/CD across accounts needs no extra plumbing:** apply
  `shared/github-oidc` once in the new account, and that env's
  `github-deploy-role` is created there too; the prod workflow just uses the
  prod GitHub Environment's `AWS_DEPLOY_ROLE_ARN`. Protect the `prod` GitHub
  Environment with required reviewers.
- ECR repos are per-environment already, so each account holds its own images;
  promoting an image to prod means pushing (or replicating) it into prod's ECR.

## Not yet built (by design, per current scope)

- Frontend (S3 + CloudFront) infra and its CI/CD — deliberate next phase.
- Route 53 / custom domain — waits until a domain is registered.
- HTTPS on the backend ALB — currently HTTP-only; add an ACM cert once
  Route 53 is wired up.
- DynamoDB state locking — S3-only state for now, by your choice; safe for
  solo use, add a lock table before more than one person runs `apply`.
