# modules/networking

Looks up the account's default VPC and its subnets. No resources are created
here — this module only reads existing account state so every other module
can reference `vpc_id` / `subnet_ids` consistently.

If you outgrow the default VPC later (e.g. need network isolation between
environments), this is the one module to replace — nothing else needs to
change its own logic, only the values it receives.
