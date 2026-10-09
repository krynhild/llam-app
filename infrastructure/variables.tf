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

variable "runpod_base_url" {
  description = "Base URL of the vLLM server on RunPod, e.g. https://<pod-id>-8000.proxy.runpod.net. Its API key is read from the runpod_api_key secret."
  type        = string
}

variable "runpod_model" {
  description = "Model name served by the vLLM server."
  type        = string
  default     = "Qwen/Qwen3-8B"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}
