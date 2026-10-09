#!/usr/bin/env bash
# Deploys a release: points ECS and CloudFront at it and waits for the backend to become healthy.
#
#   scripts/deploy.sh                 build and publish the current checkout, then deploy it
#   RELEASE=<sha> scripts/deploy.sh   deploy a release already published by GitHub Actions
#
# Extra arguments are passed to the main `terraform apply`, e.g. `scripts/deploy.sh -auto-approve`.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
tf() { terraform -chdir="$root/infrastructure" "$@"; }

if [[ -n "${RELEASE:-}" ]]; then
  release="$RELEASE"
  build=false
else
  release="$(git -C "$root" rev-parse HEAD)"
  if ! git -C "$root" diff --quiet HEAD; then
    release="$release-dirty-$(date +%Y%m%d%H%M%S)"
  fi
  build=true
fi

echo "==> Initializing Terraform"
tf init -input=false

echo "==> Ensuring the ECR repository, site bucket and RunPod API key secret exist"
tf apply -input=false -auto-approve \
  -target=aws_ecr_repository.backend -target=aws_s3_bucket.site -target=aws_secretsmanager_secret.runpod_api_key \
  -var "release=$release"

export ECR_REPOSITORY_URL SITE_BUCKET AWS_REGION RELEASE="$release"
ECR_REPOSITORY_URL="$(tf output -raw ecr_repository_url)"
SITE_BUCKET="$(tf output -raw site_bucket)"
AWS_REGION="$(tf output -raw aws_region)"

runpod_secret="$(tf output -raw runpod_api_key_secret_arn)"
aws secretsmanager get-secret-value --secret-id "$runpod_secret" --query VersionId --output text >/dev/null 2>&1 || {
  echo "The RunPod API key secret has no value yet. Set it, then rerun this script:" >&2
  echo "  aws secretsmanager put-secret-value --secret-id '$runpod_secret' --secret-string '<vLLM API key>'" >&2
  exit 1
}

if [[ "$build" == true ]]; then
  "$root/scripts/publish.sh"
else
  aws ecr describe-images --repository-name "${ECR_REPOSITORY_URL#*/}" --image-ids "imageTag=$release" >/dev/null ||
    { echo "Backend image $release is not published" >&2; exit 1; }
  aws s3api head-object --bucket "$SITE_BUCKET" --key "releases/$release/index.html" >/dev/null ||
    { echo "Frontend release $release is not published" >&2; exit 1; }
fi

echo "==> Deploying release $release"
tf apply -input=false -var "release=$release" "$@"

# Only these URLs are shared between releases.
aws cloudfront create-invalidation \
  --distribution-id "$(tf output -raw cloudfront_distribution_id)" \
  --paths "/" "/index.html" "/version.json" >/dev/null

echo "==> Deployed $release: $(tf output -raw site_url)"
