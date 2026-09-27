---
name: poshan-infra-ops
description: Use this skill to create, update, or destroy Poshan for Life's AWS infrastructure (GitHub Actions OIDC provider, or the backend app's ECS+ALB stack and its GitHub deploy role) in any environment (dev/qa/prod), via the Terraform in .iac. Trigger on requests like "deploy infra", "apply terraform", "spin up qa", "tear down dev backend", "create a prod environment", or similar.
---

# Poshan Infra Ops

Operates the Terraform under `.iac/` in this repo: `bootstrap`, `shared/github-oidc`,
and `environments/<env>/<component>`. Read `.iac/README.md` first if you haven't
already this session — it has the full architecture, apply order, and free-tier
cost table this skill assumes.

**Core rule: never guess at destructive or costly actions.** This skill exists
specifically to make sure the right questions get asked before anything is
created or destroyed — follow the "Always ask" list below every time, even if
it feels repetitive.

## Always ask the user before acting

For ANY apply or destroy, confirm explicitly (don't assume defaults silently):

1. **Which target?** One of:
   - `bootstrap` (state bucket — rarely touched after first creation)
   - `shared/github-oidc` (GitHub Actions identity provider, one per AWS account)
   - (`shared/jenkins` / `modules/jenkins` are DEPRECATED — never apply them)
   - `environments/<dev|qa|prod>/<backend|frontend>`
2. **Which action?** `apply` (create/update) or `destroy` (tear down).
3. **Required variables for that target**, if not already in a `terraform.tfvars`
   file at that path:
   - `state_bucket_name` (bootstrap only — must match the `bucket` in every
     backend config that stores state in that account)
   - **Which AWS account?** Confirm `allowed_account_ids` (and `aws_profile`,
     if not the default) for the target, and run
     `aws sts get-caller-identity` with that profile to show the user the
     resolved account before planning. Never apply with `allowed_account_ids`
     empty. qa/prod may live in separate accounts — see "Multi-account" in
     `.iac/README.md`.
   - `ssh_allowed_cidr` — the user's current IP. Get it fresh each time with
     `curl ifconfig.me` rather than reusing an old value (home/office IPs
     change) — ask the user to confirm before applying.
   - `key_pair_name` — an EXISTING EC2 key pair name. If the user doesn't have
     one yet in `ap-south-1`, offer to create it:
     `aws ec2 create-key-pair --key-name <name> --region ap-south-1 --query 'KeyMaterial' --output text > <name>.pem && chmod 400 <name>.pem`
4. **Plan before apply, always.** Run `terraform plan` and show the user a
   summary of what will change before running `terraform apply`. Never pass
   `-auto-approve` unless the user explicitly says to skip confirmation for
   this run.
5. **For `prod`, always get a second explicit confirmation** ("yes, apply to
   prod" / "yes, destroy prod") after showing the plan — treat prod
   differently from dev/qa even if the user seems in a hurry.

## Operation: first-time bootstrap

Only needed once, ever, for the whole project.

```bash
cd .iac/bootstrap
terraform init
terraform plan    # state_bucket_name defaults to "poshan-backend-terraform-state"
terraform apply
```

One bucket, `poshan-backend-terraform-state`, holds all remote state. Bootstrap
creates a folder (key prefix) per environment — `local/`, `dev/`, `qa/`,
`prod/` — and each config's backend `key` must sit under its environment:
`<env>/<component>/terraform.tfstate` (e.g. `dev/backend/terraform.tfstate`).
Per-account shared configs use `shared/<name>/terraform.tfstate` (e.g. `shared/github-oidc/terraform.tfstate`).

## Operation: apply an existing target (github-oidc, or dev/backend)

```bash
cd .iac/<target-path>          # e.g. .iac/shared/github-oidc or .iac/environments/dev/backend
# first time only:
cp backend-config.hcl.example backend-config.hcl   # fill in real state_bucket_name
cp terraform.tfvars.example terraform.tfvars         # fill in ssh_allowed_cidr, key_pair_name
terraform init -backend-config=backend-config.hcl
terraform plan
# show the plan to the user, get confirmation, then:
terraform apply
```

`shared/github-oidc` must be applied in an account before any
`environments/<env>/backend` there (the deploy role looks the provider up).

After `environments/<env>/backend` applies: surface `alb_dns_name` and the
`github_environment_variables` output, and tell the user to add each entry as
a variable on the matching GitHub Environment (Settings → Environments →
`<env>`). The deploy workflow skips its deploy job until those exist.

## Stopping vs destroying

If the user wants an env "off" to save credits (overnight, between test
cycles), use the **`poshan-env-power`** skill (`.iac/SKILLS/poshan-env-power`)
instead of destroying: it scales compute to 0 and back in minutes without
touching Terraform state. Only destroy when the env is going away for good or
the ALB's ~$25/mo must stop too. Terraform ignores the runtime counts it
changes, so a stopped env stays stopped across applies.

## Operation: destroy a target

**Ask which target and get explicit confirmation of the target name before
running anything.** Then:

```bash
cd .iac/<target-path>
terraform plan -destroy
# show what will be destroyed, get explicit user confirmation, then:
terraform destroy
```

Special cases to check and mention to the user before destroying:

- **`bootstrap`**: the state bucket has `prevent_destroy` set. Don't attempt
  to remove this lifecycle guard on the user's behalf — if they truly want
  the bucket gone, tell them to edit `.iac/bootstrap/main.tf` themselves,
  remove the `lifecycle` block, then destroy, and only after confirming no
  other environment's remote state still lives in that bucket.
- **`shared/github-oidc`**: destroying breaks every environment's GitHub
  deploys in that account (their roles trust this provider). Only destroy it
  after all `environments/<env>/backend` in the account are gone.
- **`environments/<env>/backend`**: destroying removes the ECS service, ALB,
  and EC2 capacity — but the ECR repository and its images ARE also destroyed
  by default (no `prevent_destroy` on it in the current module). If the user
  wants to keep built images, tell them to `aws ecr batch-get-image` /
  otherwise back up what they need before destroying, or ask whether they
  want the module updated to protect the ECR repo first.

## Operation: stand up a NEW environment (qa or prod)

`environments/qa/*` and `environments/prod/*` are placeholders today. To turn
one into a real, applyable environment:

1. Ask the user: which environment (`qa` or `prod`), and for prod specifically,
   confirm they've reviewed the free-tier assumptions baked into the modules
   (see `environments/prod/backend/README.md`) — a prod environment may
   warrant different sizing (bigger instance type, HTTPS via ACM, tighter
   security groups) rather than reusing dev's defaults as-is.
2. Copy `environments/dev/backend/main.tf` into `environments/<env>/backend/`,
   changing:
   - `app_name` to `poshan-backend-<env>` (e.g. `poshan-backend-qa`)
   - the backend `key` in the `terraform { backend "s3" {} }` block to
     `<env>/backend/terraform.tfstate`
   - any variables the user wants to size differently (instance_type, task_cpu,
     task_memory) — ask rather than assuming dev's values are right for this env
3. Copy `terraform.tfvars.example` and `backend-config.hcl.example` the same
   way, fill in real values (same process as "apply an existing target" above).
4. Proceed as a normal apply, following the confirmation rules above.
5. Update this repo's `.iac/README.md` "environments" table/structure section
   to reflect the new environment is now real, not a placeholder, so the next
   person (or next skill invocation) doesn't treat it as unbuilt.

Frontend environments (`environments/<env>/frontend/`) aren't scaffolded yet
at the module level (S3+CloudFront per the deployment guide's Part 5) — if
the user asks for one, say so plainly and ask whether they want that module
built first, rather than approximating it ad hoc.

## Cost guardrails to keep front-of-mind

The account is on AWS's credit-based **Free plan** (no 750-hour free EC2 /
ALB allowance) — every running resource draws down credits. See the cost
table in `.iac/README.md` and quote the monthly delta before applying.

- Every public IPv4 address costs ~$3.65/month (attached or not); the ALB
  bills one per enabled AZ, so keep `availability_zones` at 2 unless asked.
- Don't reintroduce a self-hosted CI server (Jenkins etc.) without flagging
  its cost — CI/CD is GitHub Actions on purpose.

- Never provision more than one EC2 instance per environment backend
  without asking first — the whole design assumes single-instance,
  free-tier-eligible capacity (see `.iac/README.md`'s free-tier table).
- Flag it to the user if a requested change would add a resource with no free
  tier (a second ALB, a NAT Gateway, an Elastic IP left dangling, a Route 53
  hosted zone) — don't apply it silently even if Terraform would allow it.
- If unsure whether an instance type is still free-tier-eligible in
  `ap-south-1`, say so and suggest the user verify in the AWS Billing
  console's Free Tier page rather than asserting it confidently.
