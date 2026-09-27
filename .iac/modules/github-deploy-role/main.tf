############################################
# Module: github-deploy-role
############################################
# One IAM role per environment that a GitHub Actions workflow can assume via
# OIDC (no stored AWS keys). Scoped to exactly what a deploy needs:
#   - push images to THIS environment's ECR repo
#   - register a new task definition revision
#   - update THIS environment's ECS service
#   - pass THIS environment's task execution role to ECS
#
# Trust is pinned to one repo AND one GitHub Environment (e.g. "dev"), so a
# workflow run for another environment — or from a fork — can't assume it.
# Use GitHub Environment protection rules (required reviewers) on "prod".
#
# Requires shared/github-oidc to be applied in the same AWS account first.

locals {
  owner_repo = split("/", var.github_repository)
  # Must match GitHub's sub claim format exactly (see var.github_immutable_ids).
  subject_repo = var.github_immutable_ids == null ? "repo:${var.github_repository}" : format(
    "repo:%s@%d/%s@%d", local.owner_repo[0], var.github_immutable_ids.owner_id, local.owner_repo[1], var.github_immutable_ids.repository_id
  )
}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_role" "deploy" {
  name = var.role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = data.aws_iam_openid_connect_provider.github.arn }
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "${local.subject_repo}:environment:${var.github_environment}"
        }
      }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "deploy" {
  name = "${var.role_name}-policy"
  role = aws_iam_role.deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*" # account-level API, has no resource ARN
      },
      {
        Sid    = "EcrPushThisRepo"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Resource = var.ecr_repository_arn
      },
      {
        Sid      = "TaskDefinitions"
        Effect   = "Allow"
        Action   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
        Resource = "*" # these ECS APIs don't support resource-level permissions
      },
      {
        Sid      = "DeployThisService"
        Effect   = "Allow"
        Action   = ["ecs:DescribeServices", "ecs:UpdateService"]
        Resource = var.ecs_service_arn
      },
      {
        Sid      = "PassTaskRolesToEcs"
        Effect   = "Allow"
        Action   = ["iam:PassRole"]
        Resource = var.pass_role_arns
        Condition = {
          StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" }
        }
      },
    ]
  })
}
