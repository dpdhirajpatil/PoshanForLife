# Bootstrap

Run this folder FIRST, exactly once, before any other `.iac` folder.

```bash
cd .iac/bootstrap
terraform init
terraform apply   # state_bucket_name defaults to "poshan-backend-terraform-state"
```

Bucket names are global across all of AWS, so pick something unlikely to collide
(e.g. include your AWS account ID: `aws sts get-caller-identity --query Account --output text`).

This uses **local state** (a `terraform.tfstate` file in this folder) because the
S3 bucket it creates doesn't exist yet for it to use as a backend. That's expected
and correct — don't try to migrate this one folder to remote state.

**One bootstrap per AWS account.** If qa/prod move to separate accounts, each
account gets its own state bucket via a separate local workspace — see
"Multi-account" in `.iac/README.md`. The default workspace is the current
(dev) account's `poshan-backend-terraform-state`.

**Do not delete this bucket** while any other environment still has remote state
pointing at it. `prevent_destroy` is set on the bucket for this reason.

After this succeeds, note the bucket name — you'll pass it into every other
module's backend config (`shared/github-oidc`, `environments/dev/backend`, etc.).
