terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # 팀원과 상태 공유 시 아래 주석 해제 + S3 버킷 이름 지정
  # backend "s3" {
  #   bucket = "devcos-team2-terraform-state"
  #   key    = "load-test/terraform.tfstate"
  #   region = "ap-northeast-2"
  # }
}

provider "aws" {
  region = var.aws_region
}

# ── Amazon Linux 2023 최신 AMI 자동 조회 ───────────────────────────────────
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ── SSH 키 페어 ────────────────────────────────────────────────────────────
resource "aws_key_pair" "load_test" {
  key_name   = "devcos-team2-load-test-key"
  public_key = file(var.ssh_public_key_path)
}

# ── Security Group ─────────────────────────────────────────────────────────
resource "aws_security_group" "load_test" {
  name        = "devcos-team2-load-test-sg"
  description = "Load test EC2 security group"

  # SSH
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.k6_runner_cidr]
  }

  # Spring Boot (k6 → 앱)
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.k6_runner_cidr]
  }

  # 모든 아웃바운드 허용 (Docker pull, 외부 연동)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "devcos-team2-load-test-sg"
    Project = "devcos-team2"
    Purpose = "load-test"
  }
}

# ── EC2 인스턴스 ────────────────────────────────────────────────────────────
resource "aws_instance" "load_test" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.load_test.key_name
  vpc_security_group_ids = [aws_security_group.load_test.id]

  # t3.micro는 기본적으로 CPU 크레딧 unlimited 비활성 → 크레딧 소진 주의
  credit_specification {
    cpu_credits = "standard"
  }

  # 루트 볼륨 20GB (Docker 이미지 레이어 공간 확보)
  root_block_device {
    volume_size = 20
    volume_type = "gp3"
  }

  user_data = templatefile("${path.module}/user_data.sh", {
    app_image             = var.app_image
    db_password           = var.db_password
    redis_password        = var.redis_password
    load_test_key         = var.load_test_key
    jwt_secret            = var.jwt_secret
    knowledge_github_token = var.knowledge_github_token
  })

  tags = {
    Name    = "devcos-team2-load-test"
    Project = "devcos-team2"
    Purpose = "load-test"
  }
}
