---
name: poshan-env-power
description: Start, stop, or check the status of a Poshan for Life backend environment (dev/qa/prod) on AWS by scaling its ECS service and EC2 capacity between 0 and 1 — to save AWS credits when an environment isn't in use. Trigger on requests like "stop dev", "start qa", "pause the dev environment", "shut down dev for the night", "is dev running?", "bring prod back up", "save credits", or similar. Takes the environment as input.
---

# Poshan Env Power (start / stop / status)

Scales one environment's backend compute down to zero and back, using
`scripts/env-power.sh` next to this file. It does **not** create or destroy
infrastructure — that's the `poshan-infra-ops` skill (Terraform).

```bash
.iac/SKILLS/poshan-env-power/scripts/env-power.sh status <dev|qa|prod>
.iac/SKILLS/poshan-env-power/scripts/env-power.sh stop   <dev|qa|prod>
.iac/SKILLS/poshan-env-power/scripts/env-power.sh start  <dev|qa|prod>
```

## Input: the environment

- **Required.** If the user didn't name one, ask — never default to `dev`
  silently. Valid: `dev`, `qa`, `prod`.
- `local` has no AWS stack (it's docker compose on the user's machine); say so
  rather than running anything.
- The env must already be applied (`.iac/environments/<env>/backend/terraform.tfvars`
  exists and the ECS service is live). If `status` reports the service isn't
  found, tell the user the env isn't deployed and point to `poshan-infra-ops`.

## What stop / start actually do

| | stop | start |
|---|---|---|
| ECS service `poshan-backend-<env>-service` | desired 1 → 0 (waits for tasks to stop) | desired 0 → 1 (after the host joins) |
| ASG `poshan-backend-<env>-ecs-asg` | min/desired → 0, then waits until the ASG holds no instance at all | min/desired → 1, max 1 (new instance launched) |
| Time | ~5–15 min (see draining note) | ~4–8 min, plus ~1–2 min for the ALB health check |

**ECS managed draining:** ECS attaches a lifecycle hook
(`ecs-managed-draining-termination-hook`) to the ASG, so after scale-in the
instance sits in `Terminating:Wait` — still running and **still billed** —
until ECS finishes draining it. `status` shows this as
`total-incl-terminating=1` with `in-service=0`. Only report "stopped" once
`total-incl-terminating=0`; if the script's wait times out, say the instance
is still draining and re-check `status` later.

**Saves while stopped** (approximate, ap-south-1): the t3.micro (~$8/mo), its
public IPv4 (~$3.65/mo) and its 30 GB root EBS (~$2.7/mo) — about **$14/month
per env**, prorated hourly.

**Keeps billing while stopped:** the ALB (~$18–20/mo) and its public IPs
(~$7/mo for 2 AZs), plus pennies for ECR/logs. Say this plainly every time —
"stopped" is not "$0". Getting the ALB to $0 means `terraform destroy` of the
env via `poshan-infra-ops` (which also deletes ECR images and changes the ALB
DNS name on re-create).

**Things a stop does not affect:** ECR images, task definitions, the ALB DNS
name, IAM, logs. A GitHub Actions deploy to a stopped env still succeeds
(registers the revision, service stays at 0) and the new image runs on the
next `start`.

## Always

1. **Run `status` first** and show the user the current state, so a "stop"
   on an already-stopped env (or vice versa) is a no-op you report, not run.
2. **Confirm before `stop` or `start`**, naming the env and the cost effect
   (e.g. "stop dev: saves ~$14/mo while down, ALB keeps billing ~$25/mo").
3. **`prod` needs a second explicit confirmation** ("yes, stop prod"), and
   warn that stopping prod means real downtime for real users.
4. The script enforces the account guard itself: it reads
   `allowed_account_ids` / `aws_profile` / `aws_region` from the env's
   `terraform.tfvars` and aborts if the credentials resolve to another
   account. If it aborts, report that — never edit the tfvars or pass a
   different profile to get around it.
5. After it finishes, relay the final `status` block. On `start`, if ALB
   targets aren't `healthy` after a few minutes, point to the
   `/ecs/poshan-backend-<env>` CloudWatch log group — and remember the dev
   backend has no database yet, so the real app can't pass the health check
   until one is provisioned.

## Interaction with Terraform

`modules/ecs-ec2-app` ignores the ASG's `desired_capacity`/`min_size` and the
service's `desired_count`, so `terraform apply` never silently restarts a
stopped env (or stops a running one). Consequence: a *stopped* env stays
stopped after an apply — if the user expects it up afterwards, run `start`.
