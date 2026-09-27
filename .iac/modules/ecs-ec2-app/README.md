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
