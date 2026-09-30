#!/usr/bin/env bash
# Builds and deploys the whole application to AWS.
# Extra arguments are passed to the main `terraform apply`, e.g. `scripts/deploy.sh -auto-approve`.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
tf() { terraform -chdir="$root/infrastructure" "$@"; }

image_tag="$(git -C "$root" rev-parse --short HEAD)"
if ! git -C "$root" diff --quiet HEAD; then
  image_tag="$image_tag-dirty-$(date +%Y%m%d%H%M%S)"
fi

echo "==> Initializing Terraform"
tf init -input=false

echo "==> Ensuring the ECR repository exists"
tf apply -input=false -auto-approve -target=aws_ecr_repository.backend -var "image_tag=$image_tag"

repository_url="$(tf output -raw ecr_repository_url)"
region="$(tf output -raw aws_region)"

echo "==> Building and pushing backend image $repository_url:$image_tag"
aws ecr get-login-password --region "$region" |
  docker login --username AWS --password-stdin "${repository_url%%/*}"
docker build --platform linux/arm64 -t "$repository_url:$image_tag" "$root/backend"
docker push "$repository_url:$image_tag"

echo "==> Applying infrastructure (waits for the backend to become healthy)"
tf apply -input=false -var "image_tag=$image_tag" "$@"

echo "==> Building and uploading frontend"
(cd "$root/frontend" && npm ci && npm run build)
bucket="$(tf output -raw site_bucket)"
aws s3 sync "$root/frontend/dist/assets" "s3://$bucket/assets" \
  --cache-control "public, max-age=31536000, immutable"
aws s3 sync "$root/frontend/dist" "s3://$bucket" --delete --exclude "assets/*" \
  --cache-control "no-cache"
aws cloudfront create-invalidation \
  --distribution-id "$(tf output -raw cloudfront_distribution_id)" --paths "/*" >/dev/null

echo "==> Deployed: $(tf output -raw site_url)"
