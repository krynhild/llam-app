locals {
  database_name = "polishwritinglab"
}

resource "aws_db_instance" "main" {
  identifier = var.name

  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 20
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = local.database_name
  username = local.database_name
  # RDS generates the password and stores it in Secrets Manager, so it never appears in Terraform state.
  manage_master_user_password = true

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false

  backup_retention_period   = 1
  copy_tags_to_snapshot     = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name}-final"
}
