output "site_url" {
  description = "Public URL of the application."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "aws_region" {
  value = data.aws_region.current.region
}

output "ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "site_bucket" {
  value = aws_s3_bucket.site.id
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.main.id
}

output "github_publish_role_arn" {
  description = "IAM role GitHub Actions assumes to publish releases."
  value       = aws_iam_role.github_publish.arn
}

output "runpod_api_key_secret_arn" {
  description = "Secret holding the RunPod vLLM API key. Set its value with `aws secretsmanager put-secret-value`."
  value       = aws_secretsmanager_secret.runpod_api_key.arn
}

output "database_secret_arn" {
  value = aws_db_instance.main.master_user_secret[0].secret_arn
}
