#!/usr/bin/env bash
# Publishes the artifacts of one release without deploying them:
#   backend image -> $ECR_REPOSITORY_URL:$RELEASE
#   frontend      -> s3://$SITE_BUCKET/releases/$RELEASE/
# Required environment: RELEASE, ECR_REPOSITORY_URL, SITE_BUCKET, AWS_REGION.
set -euo pipefail

: "${RELEASE:?}" "${ECR_REPOSITORY_URL:?}" "${SITE_BUCKET:?}" "${AWS_REGION:?}"

root="$(cd "$(dirname "$0")/.." && pwd)"
repository_name="${ECR_REPOSITORY_URL#*/}"

# Image tags are immutable, so an already published release is left untouched.
if aws ecr describe-images --repository-name "$repository_name" --image-ids "imageTag=$RELEASE" >/dev/null 2>&1; then
  echo "==> Backend image $RELEASE already exists"
else
  echo "==> Building and pushing backend image $ECR_REPOSITORY_URL:$RELEASE"
  aws ecr get-login-password | docker login --username AWS --password-stdin "${ECR_REPOSITORY_URL%%/*}"
  docker buildx build --platform linux/arm64 --push -t "$ECR_REPOSITORY_URL:$RELEASE" "$root/backend"
fi

echo "==> Building and uploading frontend to s3://$SITE_BUCKET/releases/$RELEASE/"
(cd "$root/frontend" && npm ci && npm run build -- --base="/releases/$RELEASE/")
printf '{"release":"%s"}\n' "$RELEASE" >"$root/frontend/dist/version.json"
destination="s3://$SITE_BUCKET/releases/$RELEASE"
# index.html and version.json are served from the site root for whichever release is live,
# so they must be revalidated. Everything else is referenced under /releases/$RELEASE/ and never changes.
aws s3 sync "$root/frontend/dist" "$destination" --exclude "index.html" --exclude "version.json" \
  --cache-control "public, max-age=31536000, immutable"
aws s3 sync "$root/frontend/dist" "$destination" --exclude "*" --include "index.html" --include "version.json" \
  --cache-control "no-cache"
