# ── OCI 인증 ────────────────────────────────────────────────────────────────
variable "tenancy_ocid" { type = string }
variable "user_ocid" { type = string }
variable "fingerprint" { type = string }
variable "private_key_path" {
  type    = string
  default = "~/.oci/oci_api_key.pem"
}
variable "region" {
  type    = string
  default = "us-phoenix-1"
}
variable "compartment_ocid" { type = string }

# ── 공통 ─────────────────────────────────────────────────────────────────────
variable "repo_url" {
  type    = string
  default = "https://github.com/prgrms-be-devcourse/NBE8-10-final-Team02.git"
}

variable "ssh_public_key" {
  description = "인스턴스 등록용 SSH 공개키 내용"
  type        = string
}

variable "ssh_private_key_path" {
  description = "SSH 개인키 경로 (주 서버 + 모니터링 서버 공통)"
  type        = string
  default     = "~/.ssh/id_rsa"
}

variable "admin_cidr" {
  description = "SSH·관리 UI 접근 허용 CIDR (본인 IP/32 권장)"
  type        = string
  default     = "0.0.0.0/0"
}

# ── 주 서버 ──────────────────────────────────────────────────────────────────
variable "app_server_ip" {
  description = "주 서버 퍼블릭 IP (고정)"
  type        = string
}

variable "app_server_vcn_ocid" {
  description = "주 서버 VCN OCID"
  type        = string
}

variable "app_server_instance_ocid" {
  description = "주 서버 인스턴스 OCID (Rebuild용)"
  type        = string
}

variable "app_server_project_dir" {
  type    = string
  default = "/home/ubuntu/my-project"
}

variable "vertex_ai_key_path" {
  description = "로컬 vertex-ai-key.json 경로"
  type        = string
}

variable "extra_ingress_ports" {
  description = "서비스별 추가 개방 포트"
  type        = list(number)
  default     = []
}

variable "npm_email" {
  type = string
}

variable "npm_password" {
  type      = string
  sensitive = true
}

variable "fe_domain" {
  type = string
}

variable "be_domain" {
  type = string
}

variable "grafana_domain" {
  type = string
}

# ── 모니터링 서버 ─────────────────────────────────────────────────────────────
variable "monitoring_availability_domain" {
  description = "모니터링 인스턴스 가용 도메인"
  type        = string
}
