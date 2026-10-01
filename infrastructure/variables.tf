variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "name" {
  description = "Prefix for resource names."
  type        = string
  default     = "polish-writing-lab"
}

variable "release" {
  description = "Release to deploy (git commit SHA): the backend image tag and the frontend folder releases/<release>/ in S3."
  type        = string
}

variable "backend_desired_count" {
  description = "Number of backend Fargate tasks."
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}
