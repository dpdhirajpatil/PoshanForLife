# modules/ecs-ec2-app

Full stack for running one containerized app behind a stable ALB endpoint,
on free-tier EC2 capacity (ECS EC2 launch type, not Fargate):

```
Internet -> ALB (:80) -> Target Group -> ECS Service -> EC2 instance (ASG, size 1) -> container
                                                              ^
                                                    ECS agent registers this
                                                    instance to the cluster
```

Also creates: an ECR repository for the app's images, an ECS task execution
IAM role (with Secrets Manager read access scoped to `<app_name>/*`), and a
CloudWatch log group.

**First apply** uses a placeholder image (nginx) so you can confirm the ALB
health check goes green before the first GitHub Actions deploy. After
that, the deploy workflow registers new task definition revisions and updates the
service directly — Terraform is told to `ignore_changes` on the task
definition so it won't fight the deploy workflow on every `apply`.

**Fixed public IP (`enable_static_ip`, default `true`):** the ECS host
self-associates a dedicated Elastic IP on boot (see the launch template's
`user_data` in `ecs.tf`), so its public address survives every stop/start
cycle instead of changing each time the instance is replaced. Useful for
anything that allowlists this host by IP — e.g. Supabase's Network
Restrictions — since you whitelist it once (the `static_ip` output) and
never touch it again. No extra cost: AWS bills any public IPv4, Elastic or
auto-assigned, at the same $0.005/hr.

**Free HTTPS front door (`enable_https_cdn`, default `true`):** a CloudFront
distribution sits in front of the ALB, terminating TLS with CloudFront's own
default `*.cloudfront.net` certificate — no domain or ACM certificate
required. The ALB itself stays HTTP-only; CloudFront -> ALB is a server-side
hop, not a browser connection, so there's no mixed-content issue. Caching is
fully disabled (TTL=0, all headers/cookies/query strings forwarded) since
this fronts a dynamic API, not static content — CloudFront here is acting
purely as a TLS terminator. Use the `cloudfront_domain_name` output as the
API's public address instead of `alb_dns_name` wherever HTTPS is required
(a browser, a future frontend). When this is enabled, the ALB's security
group is also locked down to CloudFront's own IP ranges (the
`com.amazonaws.global.cloudfront.origin-facing` managed prefix list), so
CloudFront becomes the only way in — direct `http://<alb-dns-name>` access
from the internet stops working, which is the point. Once a real domain
exists, add it as a CloudFront alias with a real ACM certificate (must be in
`us-east-1` for CloudFront) — nothing about the ALB or ECS service changes.
Cost: no free-tier dependency, pay-as-you-go on requests + data transfer,
negligible at dev-scale traffic.

**Switching to Fargate later:** change `requires_compatibilities` to
`["FARGATE"]`, `network_mode` to `"awsvpc"`, add a `network_configuration`
block to the service, change the target group's `target_type` to `"ip"`,
remove the ASG/capacity-provider/launch-template resources, and add a
Fargate capacity provider. Budget for a NAT Gateway (~$32/month) if tasks
need to reach the internet from private subnets — that's the real new cost
of that move, not the Terraform change itself.

**Free tier notes:**
- ECS control plane: always free.
- EC2 instance (t3.micro/t2.micro): 750 hrs/month free for 12 months.
- ALB: 750 hrs/month + 15 LCUs free for 12 months, then ~$16-20/month baseline.
- ECR: 500 MB storage free for 12 months; lifecycle policy caps at 10 images.
- CloudWatch Logs: retention capped at 14 days to limit storage growth.
- CloudFront: no free tier dependency either way — pay-as-you-go, but
  negligible at dev-scale traffic (typically well under $1/month).
