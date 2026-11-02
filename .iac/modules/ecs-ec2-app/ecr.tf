############################################
# ECR: image repository for this app/environment
############################################
# Free tier: 500 MB/month storage free for 12 months, then $0.10/GB-month.
# A Spring Boot jar image is typically 150-300MB, so a handful of tagged
# versions can exceed 500MB — the lifecycle policy below keeps only the
# last 10 images so this doesn't grow unbounded.

resource "aws_ecr_repository" "app" {
  name = var.app_name
  # A pushed tag can never be repointed at a different image, so the tag in
  # a task definition always means exactly the image that was deployed.
  # Deploys tag with <commit sha>-<run id>-<attempt>, unique per push.
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = var.tags
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
