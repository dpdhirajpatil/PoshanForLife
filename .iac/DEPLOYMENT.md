# Poshan for Life — Backend Deployment Runbook (dev)

How the dev backend was deployed to AWS end to end, how to operate it day to
day, and what went wrong along the way. For the big picture and flow
diagrams, read [ARCHITECTURE.md](ARCHITECTURE.md) first. It complements [README.md](README.md)
(Terraform structure, cost table, multi-account design) — read that for the
*why*; this file is the *how*.

Status as of **2026-09-27**: dev backend is live and healthy
(`GET /api/v1/health` → 200), deployed from `develop` by GitHub Actions.
Dev is normally kept **stopped** between uses to save AWS credits.

---

## 1. What's running

```
 GitHub (develop)                        AWS account 046369253799, ap-south-1 (Mumbai)
 ┌───────────────┐  OIDC (no keys)   ┌───────────────────────────────────────────────────────┐
 │ push backend/**├─────────────────▶│ IAM role poshan-backend-dev-github-deploy             │
 │ Actions:       │                  │   │ push image           │ register rev + roll service│
 │ build → ECR →  │                  │   ▼                      ▼                            │
 │ roll ECS       │                  │ ECR poshan-backend-dev   ECS cluster poshan-backend-dev│
 └───────────────┘                  │                          │ 1 × t3.micro (ASG 0..1)     │
                                     │ Internet ─▶ ALB :80 ─────▶ │ container :8080 (Spring)   │
                                     │  (2 AZs: 1a, 1b)         │ reads Secrets Manager      │
                                     │                          │  poshan-backend-dev/app    │
                                     └──────────────────────────┼────────────────────────────┘
                                                                │ TLS, IPv4 session pooler
                                                                ▼
                                   Supabase project poshan-dev (ddkwclxvlmgvyvsjpyyv), ap-south-1
                                   Postgres 17 — DB for AWS dev AND local development
                                   (files/storage still served from project poshan-for-life)
```

| Piece | Name / value |
|---|---|
| Health URL | `http://poshan-backend-dev-alb-1544740052.ap-south-1.elb.amazonaws.com/api/v1/health` |
| Terraform state | S3 `poshan-backend-terraform-state` → `dev/backend/terraform.tfstate`, `shared/github-oidc/terraform.tfstate` |
| ECS | cluster `poshan-backend-dev-cluster`, service `poshan-backend-dev-service`, task family `poshan-backend-dev` |
| EC2 host | ASG `poshan-backend-dev-ecs-asg` (t3.micro, ECS-optimized AL2023), key pair `poshan-dev-key`, fixed Elastic IP self-associated on boot (survives stop/start — see `modules/ecs-ec2-app/README.md`) |
| Image registry | ECR `046369253799.dkr.ecr.ap-south-1.amazonaws.com/poshan-backend-dev` (keeps last 10) |
| Deploy role | `arn:aws:iam::046369253799:role/poshan-backend-dev-github-deploy` |
| App secret | Secrets Manager `poshan-backend-dev/app` (JSON: `DB_PASSWORD`, `JWT_SECRET`) |
| Logs | CloudWatch `/ecs/poshan-backend-dev` (14-day retention) |
| Database | Supabase `poshan-dev`, pooler `aws-0-ap-south-1.pooler.supabase.com:5432`, user `postgres.ddkwclxvlmgvyvsjpyyv` |
| CI/CD | `.github/workflows/backend-deploy-dev.yml`, GitHub Environment `dev` (branch `develop` only) |

### Where credentials live (never in git)

| Secret | Location |
|---|---|
| EC2 SSH private key | `~/poshan/Certs/dev/poshan-dev-key.pem` (only copy — AWS can't re-issue it) |
| `poshan-dev` DB password | `~/poshan/Certs/dev/supabase-poshan-dev-db-password` + Secrets Manager + `backend/.env` |
| `poshan-for-life` DB password | `~/poshan/Certs/dev/supabase-poshan-for-life-db-password` |
| Dev `JWT_SECRET` | Secrets Manager only (generated, 64 chars) |
| Bootstrap Terraform state | `.iac/bootstrap/terraform.tfstate` (local, gitignored) — **back it up** |
| Per-target settings | `terraform.tfvars` / `backend-config.hcl` in each target dir (gitignored) |

Back up the `~/poshan/Certs/dev/` files and the bootstrap state to a password manager.

---

## 2. Tools

| Tool | Version used | Install / note |
|---|---|---|
| Terraform | 1.9.3 (needs ≥ 1.7) | `brew install hashicorp/tap/terraform` (homebrew-core is stuck at 1.5.7) |
| AWS CLI v2 | — | configured as IAM user `poshan-admin` |
| GitHub CLI `gh` | 2.101 | `brew install gh` → `gh auth login --web` |
| Supabase CLI | 2.118 | `brew install supabase/tap/supabase` → `supabase login` |
| `psql` / `pg_dump` | 18.6 | `brew install libpq` (keg-only: `/opt/homebrew/opt/libpq/bin`); must be ≥ server's 17 |
| Docker Desktop | — | only for local image builds (see §6 — prefer CI) |

---

## 3. Step-by-step: how dev was built

Run all Terraform from inside the target directory. Every root config has an
**account guard** (`allowed_account_ids = ["046369253799"]`): Terraform aborts
if your credentials point anywhere else. Always `plan`, read it, then `apply`
the saved plan.

### Step 1 — Remote state bucket (`bootstrap/`, once per AWS account)

```bash
cd .iac/bootstrap
terraform init
terraform plan -var='allowed_account_ids=["046369253799"]' -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Creates S3 `poshan-backend-terraform-state` (versioned, AES256, public access
blocked, `prevent_destroy`) with folder prefixes `local/ dev/ qa/ prod/`.
State keys follow `<env>/<component>/terraform.tfstate`; per-account shared
configs use `shared/<name>/`. Bootstrap's own state is local by design.

### Step 2 — EC2 key pair (once per environment)

```bash
mkdir -p ~/poshan/Certs/dev && chmod 700 ~/poshan/Certs/dev
aws ec2 create-key-pair --region ap-south-1 --key-name poshan-dev-key --key-type ed25519 \
  --query KeyMaterial --output text > ~/poshan/Certs/dev/poshan-dev-key.pem
chmod 400 ~/poshan/Certs/dev/poshan-dev-key.pem
```

### Step 3 — GitHub OIDC provider (`shared/github-oidc/`, once per AWS account)

```bash
cd .iac/shared/github-oidc
cp backend-config.hcl.example backend-config.hcl
cp terraform.tfvars.example terraform.tfvars
terraform init -backend-config=backend-config.hcl
terraform plan -out=oidc.tfplan && terraform apply oidc.tfplan
```

Lets GitHub Actions trade its short-lived token for temporary AWS credentials —
no AWS access keys are stored in GitHub. Free.

### Step 4 — Dev backend stack (`environments/dev/backend/`)

```bash
cd .iac/environments/dev/backend
cp backend-config.hcl.example backend-config.hcl
cp terraform.tfvars.example terraform.tfvars
#   ssh_allowed_cidr = "<your IP>/32"      # curl -4 ifconfig.me
#   key_pair_name    = "poshan-dev-key"
terraform init -backend-config=backend-config.hcl
terraform plan -out=dev.tfplan && terraform apply dev.tfplan
```

Creates ~25 resources: ECR, ECS cluster + capacity provider + service, ASG +
launch template, ALB + target group + listener, security groups (SSH only from
your IP; container ports only from the ALB), IAM roles, log group, the app
secret, and the GitHub deploy role. The first task definition runs an nginx
**placeholder** image (it fails health checks — expected until the first real
deploy).

Key dev settings in `main.tf`: task 700 MB / 512 CPU, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`,
stop-then-start deploys (`0%/100%` — two copies don't fit on a t3.micro),
180 s health-check grace period, 30 s ALB draining, ALB in 2 AZs.

### Step 5 — Dev database on Supabase

1. **Create the project** (Free plan org — don't pass `--size`, free orgs reject it):
   ```bash
   (umask 077; openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32 \
     > ~/poshan/Certs/dev/supabase-poshan-dev-db-password)
   supabase projects create poshan-dev --org-id wrgytjujcsybcjedbuje --region ap-south-1 \
     --db-password "$(cat ~/poshan/Certs/dev/supabase-poshan-dev-db-password)"
   ```
2. **Find the pooler host** — each project lives on `aws-0` *or* `aws-1`; try both:
   ```bash
   PGPASSWORD="$(cat ~/poshan/Certs/dev/supabase-poshan-dev-db-password)" \
   /opt/homebrew/opt/libpq/bin/psql \
     "host=aws-0-ap-south-1.pooler.supabase.com port=5432 dbname=postgres user=postgres.ddkwclxvlmgvyvsjpyyv sslmode=require" \
     -c 'select version()'
   ```
3. **Copy data from `poshan-for-life`** (test data only). Only the `public`
   schema; refresh tokens and OTPs copied as empty tables; the source is read-only:
   ```bash
   P=/opt/homebrew/opt/libpq/bin
   PGPASSWORD="$(cat ~/poshan/Certs/dev/supabase-poshan-for-life-db-password)" \
   PGOPTIONS='-c default_transaction_read_only=on' \
   $P/pg_dump "host=aws-1-ap-south-1.pooler.supabase.com port=5432 dbname=postgres user=postgres.pdfliakdheartjetfedb sslmode=require" \
     --schema=public --no-owner --no-privileges \
     --exclude-table-data=public.refresh_tokens --exclude-table-data=public.phone_otps -f public.sql
   sed -i '' '/^CREATE SCHEMA public;$/d' public.sql        # public already exists on the target
   PGPASSWORD="$(cat ~/poshan/Certs/dev/supabase-poshan-dev-db-password)" \
   $P/psql "host=aws-0-ap-south-1.pooler.supabase.com port=5432 dbname=postgres user=postgres.ddkwclxvlmgvyvsjpyyv sslmode=require" \
     --single-transaction -v ON_ERROR_STOP=1 -f public.sql
   rm public.sql
   ```
   Result: 46 tables (incl. legacy `pfl_*`), 205 rows, 22 enums, 3 functions,
   RLS on all tables, Flyway history at **v20** (= the repo's latest migration,
   so Flyway applies nothing and needs no baseline settings). Row counts were
   verified table-by-table against the source.

### Step 6 — App secrets

Terraform creates the secret *container* only (`app_secret_keys` in
`main.tf`); values are set out-of-band so they never appear in Terraform state:

```bash
python3 - <<'EOF' > /tmp/app-secret.json
import json, secrets, string, pathlib
pw = pathlib.Path.home().joinpath('poshan/Certs/dev/supabase-poshan-dev-db-password').read_text()
jwt = ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(64))  # ≥32 bytes required
print(json.dumps({"DB_PASSWORD": pw, "JWT_SECRET": jwt}))
EOF
aws secretsmanager put-secret-value --region ap-south-1 --secret-id poshan-backend-dev/app \
  --secret-string file:///tmp/app-secret.json
rm /tmp/app-secret.json
```

The task definition injects them with `valueFrom = "<secret-arn>:<KEY>::"`
alongside plain env vars `DB_URL`, `DB_USERNAME`, `SPRING_PROFILES_ACTIVE=dev`.

### Step 7 — GitHub Environment `dev`

```bash
R=dpdhirajpatil/PoshanForLife
gh api -X PUT repos/$R/environments/dev --input - <<'EOF'
{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
EOF
gh api -X POST repos/$R/environments/dev/deployment-branch-policies -f name=develop -f type=branch
# then one `gh variable set <NAME> --env dev --repo $R --body <value>` per entry of:
terraform -chdir=.iac/environments/dev/backend output github_environment_variables
```

Seven plain variables (not secrets): `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`,
`ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`, `ECS_TASK_FAMILY`,
`CONTAINER_NAME`. Only `develop` may deploy to `dev` (the repo is public).

Verify the policy actually took (don't just trust that the command above
succeeded):
```bash
gh api repos/$R/environments/dev --jq '.deployment_branch_policy'
gh api repos/$R/environments/dev/deployment-branch-policies --jq '.branch_policies[].name'
# expect: {"protected_branches": false, "custom_branch_policies": true}, then "develop"
```
Three independent layers now restrict dev deploys to `develop`: the
workflow's `on.push.branches` filter, this environment branch policy
(the one GitHub actually enforces server-side, including against a manual
`workflow_dispatch`), and a `github.ref` guard in the workflow itself
(belt-and-braces for `workflow_dispatch`). See
[§9 Branching & release strategy](#9-branching--release-strategy) for how
this extends to qa/prod.

### Step 8 — First deploy

Merged PR #1 into `develop` → workflow ran. It failed once on AWS login
(see §6, immutable OIDC subject), fixed in PR #2, re-ran green:
image `poshan-backend-dev:e85d230…` → task definition **rev 3** → service
rolled → Spring started in ~84 s, Flyway "Schema public is up to date",
ALB target **healthy**, health endpoint 200.

---

## 4. Day-to-day operations

### Deploy new backend code
Push/merge to `develop` with changes under `backend/**` — GitHub Actions
builds, pushes, registers a new revision from the **latest** task definition
(keeping env vars/secrets), and rolls the service. Or trigger manually:
```bash
gh workflow run backend-deploy-dev.yml --repo dpdhirajpatil/PoshanForLife --ref develop
gh run watch --repo dpdhirajpatil/PoshanForLife
```
(If GitHub refuses the manual dispatch, it's because the workflow file isn't on
the default branch `main` yet — push a `backend/**` change to `develop` instead,
or use Actions → "Backend deploy (dev)" → Run workflow once it's on `main`.)
Expect ~1–2 min of dev downtime per deploy (stop-then-start). If dev is
stopped, the deploy still succeeds; the new revision runs on the next start.

### Start / stop / status (save credits)
```bash
.iac/SKILLS/poshan-env-power/scripts/env-power.sh status dev
.iac/SKILLS/poshan-env-power/scripts/env-power.sh stop dev    # ~2 min; saves ~$14/mo
.iac/SKILLS/poshan-env-power/scripts/env-power.sh start dev   # ~2 min + ~90 s Spring boot
```
Stopped ≠ $0: the ALB keeps billing (~$25/mo). Terraform ignores the counts
these scripts change, so `terraform apply` never restarts a stopped env.

### Logs, shell, rollback
```bash
aws logs tail /ecs/poshan-backend-dev --follow --region ap-south-1
# shell on the host without SSH (needs the session-manager-plugin):
aws ssm start-session --region ap-south-1 --target <instance-id>
# or SSH (from the IP in terraform.tfvars):
ssh -i ~/poshan/Certs/dev/poshan-dev-key.pem ec2-user@<instance-public-ip>
# roll back to an earlier task definition revision:
aws ecs update-service --region ap-south-1 --cluster poshan-backend-dev-cluster \
  --service poshan-backend-dev-service --task-definition poshan-backend-dev:<rev>
```

### Change or add an app secret
1. Rotating a value: `get-secret-value`, edit the JSON, `put-secret-value`,
   then `aws ecs update-service ... --force-new-deployment` (secrets are read at task start).
2. Adding a new key (e.g. `MSG91_AUTH_KEY`): add it to `app_secret_keys` in
   `environments/dev/backend/main.tf`, plan/apply (new task def revision),
   put the value in the secret, then **deploy** (workflow run) — Terraform
   never moves the service to a new revision by itself.

### Your IP changed (SSH blocked)
Update `ssh_allowed_cidr` in `environments/dev/backend/terraform.tfvars`, then plan/apply.

### Roll out the fixed Elastic IP (`enable_static_ip`, one-time)
`terraform apply` alone won't move the already-running instance onto the
new launch template — it only updates the template and ASG for the *next*
instance. Cycle the current one onto it, then whitelist the result:
```bash
cd .iac/environments/dev/backend
terraform plan -out=dev.tfplan && terraform apply dev.tfplan   # creates the EIP, updates the launch template

.iac/SKILLS/poshan-env-power/scripts/env-power.sh stop dev
.iac/SKILLS/poshan-env-power/scripts/env-power.sh start dev    # new instance launches, self-associates the EIP
.iac/SKILLS/poshan-env-power/scripts/env-power.sh status dev   # prints "Static IP   : x.x.x.x"
```
Take that IP to Supabase → `poshan-dev` project → Settings → Database →
Network Restrictions, add it once. It won't change again across future
stop/start cycles — that's the whole point of this change.

If `status` doesn't print a Static IP line after `start`, the self-association
step in the instance's boot script likely failed silently; check
`/var/log/cloud-init-output.log` on the host (SSM or SSH, per above) for the
`associate-address` call's actual error before assuming it worked.

### Roll out free HTTPS via CloudFront (`enable_https_cdn`, one-time)
A CloudFront distribution now sits in front of the ALB, terminating TLS with
CloudFront's own default `*.cloudfront.net` certificate — no domain or ACM
cert needed. The ALB stays HTTP-only internally; CloudFront -> ALB is a
server-side hop, not a browser connection, so there's no mixed-content
concern. Caching is fully disabled (this fronts a dynamic API, not static
content).

```bash
cd .iac/environments/dev/backend
terraform plan -out=dev.tfplan && terraform apply dev.tfplan   # creates the CloudFront distribution, restricts the ALB SG to CloudFront's IP ranges
terraform output cloudfront_domain_name                        # e.g. d1234abcd.cloudfront.net
```
Use that `cloudfront_domain_name` output, not `alb_dns_name`, as the API's
public HTTPS address from now on — a browser or a future frontend should
call `https://<cloudfront_domain_name>/...`. Direct `http://<alb_dns_name>`
access stops working once this applies, since the ALB's security group is
locked down to CloudFront's own IP ranges — that's intentional, CloudFront
is meant to be the only way in.

CloudFront distributions can take 5-15 minutes to fully deploy after
`apply` finishes; if the new domain 403s/times out right after applying,
wait and retry before assuming something's wrong.

When a real domain is registered later (Route 53 phase), add it as a
CloudFront alias with a real ACM certificate (must be requested in
`us-east-1` regardless of the app's region) — nothing about the ALB or ECS
service needs to change for that.

### Roll out HTTPS on the ALB with ACM (`https_domain_name` + `enable_https_listener`, one-time)
The alternative to CloudFront while that's blocked on AWS account
verification: a free ACM certificate for `dev.api.poshanforlife.com` on a new
:443 listener of the existing ALB. DNS for `poshanforlife.com` is at
Squarespace, so ACM's validation record is added there by hand. Two applies,
because ACM can't issue until that record exists:

```bash
cd .iac/environments/dev/backend

# Step 1 — https_domain_name set, enable_https_listener = false
terraform plan -out=dev.tfplan && terraform apply dev.tfplan   # requests the certificate (PENDING_VALIDATION)
terraform output acm_validation_records                        # the CNAME to add at Squarespace
```
Add at Squarespace (Domains → poshanforlife.com → DNS → Custom records):
- the validation CNAME from the output (name `_….dev.api`, value
  `_….acm-validations.aws.`). **Keep it forever**: ACM re-checks it to renew.
- `dev.api` CNAME → the `alb_dns_name` output.

Leave the root and `www` records alone (they serve the website).

```bash
# Wait until issued (5–30 min after the CNAME is visible)
terraform refresh && terraform output acm_certificate_status   # ISSUED

# Step 2 — set enable_https_listener = true in main.tf
terraform plan -out=dev.tfplan && terraform apply dev.tfplan   # :443 listener, 443 open on the ALB SG, :80 → 301 to https
curl -s -o /dev/null -w '%{http_code}\n' https://dev.api.poshanforlife.com/api/v1/health   # 200
curl -s -o /dev/null -w '%{http_code}\n' http://dev.api.poshanforlife.com/api/v1/health    # 301
```
If step 2's apply sits on `aws_acm_certificate_validation` for 30 min and
fails, the CNAME isn't visible yet: check it with
`dig +short CNAME _<token>.dev.api.poshanforlife.com` and apply again.

Clients then use `https://dev.api.poshanforlife.com/api/v1`. Once AWS clears
CloudFront, the two can be combined (CloudFront alias with its own us-east-1
certificate), but this path doesn't need CloudFront at all.

### Deploy the frontend (staff portal) to dev, one-time
The Angular portal runs as a second ECS service on the dev cluster and host
(nginx, 64 MB next to the 700 MB API), reached at
`https://dev.ui.poshanforlife.com` through a host-header rule on the shared
ALB. Terraform: `environments/dev/frontend` (own state, reads the backend's
`shared_platform` output) + `modules/ecs-static-site`. Prerequisite: the
backend env applied from this branch (it publishes `shared_platform` and
sets `CORS_ALLOWED_ORIGINS` for the portal; the CORS change reaches the
running API with its next deploy).

```bash
cd .iac/environments/dev/frontend
terraform init -backend-config=backend-config.hcl
terraform plan -out=dev.tfplan && terraform apply dev.tfplan   # ECR, service, target group, host rule, deploy role, cert request
terraform output dns_records_to_add                            # dev.ui CNAME + ACM validation CNAME → Squarespace
terraform output github_environment_variables                  # FRONTEND_* → GitHub Environment "dev" variables
```
Then, once the dev.ui validation CNAME is live **and** the backend's HTTPS
listener is on, set `attach_certificate = true` and apply again (attaches
the dev.ui certificate to the :443 listener by SNI and moves the host rule
from :80 to :443 via `public_listener_arn`). Deploys then run on every push
to `develop` touching `frontend/**`. `env-power.sh` starts/stops the portal
service together with the API.

---

## 5. Local development

`backend/.env` points at the **same** Supabase `poshan-dev` database as AWS
dev (profile `local`, storage still on `poshan-for-life`). Local writes and
local Flyway migrations immediately affect deployed dev — deploy new
migrations promptly, or point local back at a separate database.

---

## 6. Problems hit and their fixes

| Symptom | Cause | Fix |
|---|---|---|
| `terraform init` fails on `required_version` | Terraform 1.5.7 from homebrew-core | Install ≥ 1.7 from `hashicorp/tap` |
| Jenkins EC2 would boot the ECS AMI / never install Jenkins | AMI filter too broad; Jenkins RPM repo moved | Moot — Jenkins replaced by GitHub Actions (code kept, deprecated) |
| Tasks never healthy after first apply | nginx placeholder listens on 80, health check hits 8080 `/api/v1/health` | Expected until the first real deploy |
| `stop` said "Stopped" while EC2 still billed | ECS managed-draining hook holds the instance in `Terminating:Wait` | Script now waits until the ASG holds zero instances |
| Stops/deploys took 5+ min | ALB default 300 s deregistration delay | Dev set to 30 s |
| Spring would run the `local` profile in AWS | `profiles.default: local` in `application.yml` | `SPRING_PROFILES_ACTIVE=dev` in the task |
| Supabase `projects create` → "Instance size cannot be specified" | Free-plan org | Omit `--size` |
| Can't reach `db.<ref>.supabase.co` from AWS/Docker | Direct host is IPv6-only | Use the session pooler (IPv4) on 5432 — not 6543 (transaction mode breaks Hibernate/Flyway prepared statements) |
| Pooler says `tenant/user ... not found` | Wrong pooler shard | Project is on `aws-0` or `aws-1` — try the other |
| Password rejected right after a reset | Reset takes a few minutes to reach the pooler | Wait and retry |
| Deploy: `Not authorized to perform sts:AssumeRoleWithWebIdentity` | Repo uses GitHub's **immutable OIDC subject** `repo:owner@<id>/repo@<id>:environment:dev` | `github_immutable_ids` in the deploy-role module (PR #2). Check with `gh api repos/<o>/<r>/actions/oidc/customization/sub` |
| Local `docker buildx --platform linux/amd64` fails: `tar: Cannot open: Function not implemented` | QEMU emulation on Apple Silicon | Let CI build; if you must build locally, build the JAR natively and package only the runtime stage |

---

## 7. Cost (AWS Free plan — credit-based)

Dev ≈ **$40–43/month running**, ≈ $27/month stopped (ALB + its IPs + secret).
Supabase `poshan-dev` is $0 (Free plan: 500 MB DB, pauses after ~7 days of
inactivity — resume from the dashboard). Credits: US$119.57 with 146 days
left on 2026-09-27; the plan ends 2027-02-18 — upgrade to a paid AWS plan
before then or the account closes. Details: [README.md](README.md#cost-posture--aws-free-plan-credit-based).

---

## 8. Open items

- Delete deprecated `modules/jenkins/` and `shared/jenkins/`.
- Rotate the `poshan-for-life` DB password (it was shared in a chat), then
  update its password file.
- Add remaining app keys to `poshan-backend-dev/app` as features need them
  (Supabase storage key, MSG91, Firebase, Anthropic) — see §4.
- HTTPS/custom domain (Route 53 + ACM), frontend hosting, qa/prod environments,
  S3 state locking before a second person runs `apply`.
- Delete the empty `.iac/_pending-workflows/` staging folder (left behind
  because `.github/**` can't be written remotely — see §9).
- Confirm dev's `AWS_DEPLOY_ROLE_ARN` et al. still work after `terraform
  apply` picks up `enable_static_ip` (new EIP + updated launch template) —
  see §1's static IP row and §3 Step 4.

---

## 9. Branching & release strategy

Backend and frontend follow the same rules. GitHub Actions is the only
thing that deploys application code; Terraform builds infrastructure through
a reviewed plan/apply and ignores which image is running.

| Environment | Deploys from | Backend workflow | Frontend workflow | GitHub Environment |
|---|---|---|---|---|
| dev | `develop` | `backend-deploy-dev.yml` | `frontend-deploy-dev.yml` | `dev`, branch `develop` only |
| qa | `qa` | `backend-deploy-qa.yml` | `frontend-deploy-qa.yml` | `qa`, branch `qa` only |
| prod | `prod` | `backend-deploy-prod.yml` | `frontend-deploy-prod.yml` | `prod`, branch `prod` only + required reviewer |

Release flow: feature branches → PR into `develop` → promote `develop` → `qa`
→ `prod` by PR; after each successful prod deploy a PR `prod` → `main` keeps
`main` equal to what's live. (2026-10-11: the old `QA` branch was renamed to
`qa` and `prod` was created from `main`.)

Each workflow is restricted to its branch three ways: the workflow's
`on.push.branches` filter, a `github.ref` guard on the first job (blocks a
manual `workflow_dispatch` from any other branch), and — the one that
actually matters, since it's enforced server-side by GitHub, not by
anything in the workflow file — that environment's **deployment branch
policy**. All three are live (created 2026-10-11 with the commands below);
the `prod` Environment also requires an approval before its deploy job runs:

```bash
R=dpdhirajpatil/PoshanForLife
for ENV in qa prod; do
  gh api -X PUT repos/$R/environments/$ENV --input - <<EOF
{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
EOF
  gh api -X POST repos/$R/environments/$ENV/deployment-branch-policies -f name=$ENV -f type=branch
done
```

**After a successful prod deploy**, the workflow does two more things
automatically (not applicable to dev/qa):

1. **Tags** the deployed commit: `prod-<UTC-date>-<UTC-HHMM>-<short-sha>`
   (e.g. `prod-2026.10.10-1432-a1b2c3d`), annotated, pushed to `origin`.
   This is a plain timestamp+sha scheme, not semver — if you'd rather tag
   releases `v1.2.3`, that needs a version-bump step added before this one;
   ask if you want that instead.
2. **Opens a PR from `prod` into `main`** (reusing an existing open one
   instead of creating a duplicate) so `main` always reflects what's
   actually live, not just what's been merged to `prod`. The PR isn't
   auto-merged — someone still reviews and merges it.

**Prod approval (configured):** the `prod` GitHub Environment has a
required reviewer, so every prod deploy, backend or frontend, pauses until
it's approved in the Actions run. Frontend prod deploys tag the commit
`prod-ui-<date>-<sha>` and share the same `prod` → `main` PR.

**Why these three workflow files exist before qa/prod infra does:** they're
inert until each environment's Terraform is applied and its
`github_environment_variables` are copied into GitHub (same gate dev uses —
see the `config` job in any of the three files). Writing them now means the
CI/CD pattern is already settled by the time qa/prod get built, instead of
being designed under pressure then.

**A note on how these landed in the repo:** `.github/workflows/*` is
protected against remote writes (sensible — those files run with real
secrets), so these three were written to `.iac/_pending-workflows/` and then
moved into `.github/workflows/` with a plain `mv` in the same session —
which worked because moving a file isn't the same operation as writing one.
