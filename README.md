# Polish Writing Lab

A monorepo for an evidence-grounded Polish writing coach.

## Projects

- `backend/` — Java 25 and Spring Boot API
- `frontend/` — React and TypeScript web application
- `infrastructure/` — Terraform configuration for AWS
- `scripts/deploy.sh` — builds and deploys everything to AWS

## Local development

### Backend

Requires Docker: Spring Boot starts the PostgreSQL container from `backend/compose.yaml` automatically.

```bash
cd backend
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Infrastructure

```bash
cd infrastructure
terraform init -backend=false
terraform validate
terraform test   # uses a mocked AWS provider, no credentials needed
```

## AWS deployment

```
CloudFront ─┬─ /*      → S3 (React build, private, Origin Access Control)
            └─ /api/*  → internal ALB (VPC origin) → ECS Fargate (Spring Boot) → RDS PostgreSQL
```

- The React app and the API share the CloudFront domain, so the frontend calls relative `/api` URLs.
- The ALB is internal and only reachable through the CloudFront VPC origin.
- Fargate tasks run in private subnets (ARM64). RDS runs in isolated subnets and accepts connections only from the tasks.
- RDS generates the database password and keeps it in Secrets Manager (never in Terraform state). ECS injects it as `SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD`.
- The ALB health check calls `/actuator/health/readiness`, which includes database connectivity.

### Releases

A release is identified by a git commit SHA and consists of two immutable artifacts:

- the backend image `<ecr-repository>:<sha>` (ECR tags are immutable)
- the frontend build in `s3://<site-bucket>/releases/<sha>/`, built with Vite `base` set to `/releases/<sha>/`

The Terraform variable `release` selects the live release for both: ECS runs the image with that tag, and CloudFront serves `index.html` from that release's folder. Assets are always requested under `/releases/<sha>/...`, so browser tabs still running an older release keep working after a switch. `https://<site>/version.json` shows the release that is live.

Publishing a release (uploading the artifacts) and deploying it (switching to it) are separate steps:

- `scripts/publish.sh` builds and uploads the artifacts. CI runs it for every commit on `main`.
- `scripts/deploy.sh` runs `terraform apply -var release=<sha>` and waits until the new backend tasks are healthy.

### Prerequisites

- Terraform 1.10+, the AWS CLI, Docker and Node.js
- AWS credentials for the target account (the region defaults to `us-east-1`; override with `-var aws_region=...` or a `terraform.tfvars` file)

### Deploy

Build, publish and deploy the current checkout (uncommitted changes get a `-dirty-<timestamp>` release):

```bash
scripts/deploy.sh
```

Deploy a release that CI already published, or roll back to an earlier one:

```bash
RELEASE=<commit sha> scripts/deploy.sh
```

The script refuses to deploy a release whose artifacts are missing. It prints the application URL at the end. The first deploy takes about 15 minutes, mostly for RDS and CloudFront.

### Publishing from GitHub Actions

Terraform creates an IAM role that only the `main` branch of `krynhild/llam-app` can assume, through GitHub's OIDC provider. The role can push images and upload to `releases/` in the bucket, but it can't deploy anything. After the first deploy, set these repository variables (Settings → Secrets and variables → Actions → Variables) from the Terraform outputs:

| Variable | Terraform output |
|---|---|
| `AWS_PUBLISH_ROLE_ARN` | `github_publish_role_arn` |
| `AWS_REGION` | `aws_region` |
| `ECR_REPOSITORY_URL` | `ecr_repository_url` |
| `SITE_BUCKET` | `site_bucket` |

Until `AWS_PUBLISH_ROLE_ARN` is set, the publish job is skipped. If the AWS account already has a GitHub OIDC provider, deploy with `-var create_github_oidc_provider=false`.

### State

Terraform state is stored locally in `infrastructure/terraform.tfstate` (gitignored). Before others deploy or CI deploys, move it to an S3 backend (see below) so everyone shares one state.

```hcl
terraform {
  backend "s3" {
    bucket       = "<your-state-bucket>"
    key          = "polish-writing-lab/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
  }
}
```

### Tear down

```bash
cd infrastructure
terraform destroy -var release=unused
```

RDS takes a final snapshot named `polish-writing-lab-final` before it is deleted. Delete that snapshot before destroying a second time, otherwise the name clashes.
