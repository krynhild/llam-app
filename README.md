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

Terraform manages the infrastructure. `scripts/deploy.sh` handles the build artifacts that Terraform doesn't: it pushes the backend image to ECR (tagged with the git commit) and uploads the frontend build to S3.

### Prerequisites

- Terraform 1.10+, the AWS CLI, Docker and Node.js
- AWS credentials for the target account (the region defaults to `eu-central-1`; override with `-var aws_region=...` or a `terraform.tfvars` file)

### Deploy

```bash
scripts/deploy.sh
```

The script:

1. creates the ECR repository if needed
2. builds and pushes the backend image
3. runs `terraform apply`, which waits until the new backend tasks are healthy
4. uploads the frontend and invalidates the CloudFront cache

It prints the application URL at the end. The first run takes about 15 minutes, mostly for RDS and CloudFront.

### State

Terraform state is stored locally in `infrastructure/terraform.tfstate` (gitignored). Before others deploy or CI deploys, move it to an S3 backend (see below) so everyone shares one state.

```hcl
terraform {
  backend "s3" {
    bucket       = "<your-state-bucket>"
    key          = "polish-writing-lab/terraform.tfstate"
    region       = "eu-central-1"
    use_lockfile = true
  }
}
```

### Tear down

```bash
cd infrastructure
terraform destroy -var image_tag=unused
```

RDS takes a final snapshot named `polish-writing-lab-final` before it is deleted. Delete that snapshot before destroying a second time, otherwise the name clashes.
