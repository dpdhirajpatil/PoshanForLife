# environments/dev/frontend — placeholder

Not built yet. Per plan, this phase covers backend deployment via GitHub Actions
first; frontend (S3 + CloudFront, per the deployment guide's Part 5) and its
own CI/CD are a deliberate next phase, not part of this initial scaffold.

When ready, this folder will likely hold: an S3 bucket (private, OAC-only
access), a CloudFront distribution, and — once Route 53 is in the picture —
the DNS records tying it to a real domain.
