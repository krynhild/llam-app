mock_provider "aws" {
  override_data {
    target = data.aws_availability_zones.available
    values = {
      names = ["eu-central-1a", "eu-central-1b", "eu-central-1c"]
    }
  }

  mock_data "aws_region" {
    defaults = {
      region = "eu-central-1"
    }
  }

  mock_resource "aws_db_instance" {
    defaults = {
      address = "db.example.internal"
      port    = 5432
      master_user_secret = [{
        secret_arn    = "arn:aws:secretsmanager:eu-central-1:123456789012:secret:rds-db-secret"
        kms_key_id    = "alias/aws/secretsmanager"
        secret_status = "active"
      }]
    }
  }

  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/pwl-role"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-central-1:123456789012:loadbalancer/app/pwl/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:eu-central-1:123456789012:targetgroup/pwl/0123456789abcdef"
    }
  }

  mock_resource "aws_cloudfront_distribution" {
    defaults = {
      domain_name = "d111111abcdef8.cloudfront.net"
    }
  }
}

variables {
  image_tag = "test"
}

run "frontend_is_served_from_private_bucket" {
  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.site.block_public_acls,
      aws_s3_bucket_public_access_block.site.block_public_policy,
      aws_s3_bucket_public_access_block.site.ignore_public_acls,
      aws_s3_bucket_public_access_block.site.restrict_public_buckets,
    ])
    error_message = "The site bucket must block all public access."
  }

  assert {
    condition     = aws_cloudfront_distribution.main.default_cache_behavior[0].target_origin_id == "site"
    error_message = "CloudFront must serve the frontend from S3 by default."
  }
}

run "api_is_routed_to_internal_alb" {
  assert {
    condition     = aws_lb.backend.internal
    error_message = "The ALB must be internal and reached only through the CloudFront VPC origin."
  }

  assert {
    condition = anytrue([
      for behavior in aws_cloudfront_distribution.main.ordered_cache_behavior :
      behavior.path_pattern == "/api/*" && behavior.target_origin_id == "backend"
    ])
    error_message = "CloudFront must route /api/* to the backend origin."
  }

  assert {
    condition     = aws_lb_target_group.backend.health_check[0].path == "/actuator/health/readiness"
    error_message = "The ALB must use the Spring Boot readiness probe."
  }
}

run "backend_runs_on_fargate_with_database_secrets" {
  assert {
    condition     = aws_ecs_task_definition.backend.runtime_platform[0].cpu_architecture == "ARM64"
    error_message = "The backend image is built for ARM64."
  }

  assert {
    condition = toset([for env in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment : env.name]) == toset([
      "SPRING_DATASOURCE_URL",
      "APP_CORS_ALLOWED_ORIGIN",
    ])
    error_message = "The backend needs the datasource URL and the allowed CORS origin."
  }

  assert {
    condition     = contains([for env in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment : env.value], "https://d111111abcdef8.cloudfront.net")
    error_message = "CORS must allow the CloudFront domain."
  }

  assert {
    condition = toset([for secret in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].secrets : secret.name]) == toset([
      "SPRING_DATASOURCE_USERNAME",
      "SPRING_DATASOURCE_PASSWORD",
    ])
    error_message = "Database credentials must come from Secrets Manager."
  }
}

run "database_is_private_and_encrypted" {
  assert {
    condition     = !aws_db_instance.main.publicly_accessible && aws_db_instance.main.storage_encrypted
    error_message = "RDS must be private and encrypted."
  }

  assert {
    condition     = aws_db_instance.main.manage_master_user_password
    error_message = "The database password must be managed by RDS, not stored in Terraform state."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.database_from_backend.referenced_security_group_id == aws_security_group.backend.id
    error_message = "Only backend tasks may connect to the database."
  }
}
