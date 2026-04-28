variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t3.small"
}

variable "app_image" {
  description = "배포할 Docker 이미지 (tag 포함)"
  type        = string
  default     = "ghcr.io/prgrms-be-devcourse/nbe8-10-final-team02-backend:latest"
}

variable "ssh_public_key_path" {
  description = "EC2에 등록할 SSH 공개키 경로"
  type        = string
  default     = "~/.ssh/my-key.pub"
}

variable "db_password" {
  description = "PostgreSQL 비밀번호"
  type        = string
  sensitive   = true
  default     = "loadtest-db-password"
}

variable "redis_password" {
  description = "Redis 비밀번호"
  type        = string
  sensitive   = true
  default     = "loadtest-redis-password"
}

variable "load_test_key" {
  description = "스텁 모드 전환용 비밀 키 (X-Load-Test-Key 헤더)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "jwt_secret" {
  description = "JWT 시크릿 (테스트용 토큰 발급에 사용)"
  type        = string
  sensitive   = true
  default     = "loadtest-jwt-secret-key-change-this-in-real-use-f8e7d6c5b4a39281"
}

variable "ghcr_token" {
  description = "GitHub PAT (read:packages) - ghcr.io 이미지 pull용"
  type        = string
  sensitive   = true
  default     = ""
}

variable "knowledge_github_token" {
  description = "GitHub PAT (레포 분석 기능용). 해당 기능 미테스트 시 빈 문자열 가능"
  type        = string
  sensitive   = true
  default     = ""
}

variable "k6_runner_cidr" {
  description = "k6를 실행할 머신의 IP CIDR (EC2 외부 실행 시). 비우면 0.0.0.0/0"
  type        = string
  default     = "0.0.0.0/0"
}
