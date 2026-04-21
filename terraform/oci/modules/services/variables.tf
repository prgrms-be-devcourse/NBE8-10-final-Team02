variable "server_ip" {
  type = string
}

variable "ssh_private_key_path" {
  type    = string
  default = "~/.ssh/id_rsa"
}

variable "repo_url" {
  type = string
}

variable "project_dir" {
  type    = string
  default = "/home/ubuntu/my-project"
}

variable "vertex_ai_key_path" {
  type = string
}

variable "instance_ocid" {
  description = "인스턴스 OCID (Rebuild용)"
  type        = string
}

variable "compartment_ocid" {
  type = string
}

variable "vcn_ocid" {
  type = string
}

variable "default_security_list_ocid" {
  description = "Default Security List OCID (import용)"
  type        = string
}


variable "admin_cidr" {
  type    = string
  default = "0.0.0.0/0"
}

variable "extra_ingress_ports" {
  type    = list(number)
  default = []
}

variable "npm_email" {
  description = "NPM 관리자 이메일"
  type        = string
}

variable "npm_password" {
  description = "NPM 관리자 비밀번호"
  type        = string
  sensitive   = true
}

variable "fe_domain" {
  description = "프론트엔드 도메인 (예: app.example.com)"
  type        = string
}

variable "be_domain" {
  description = "백엔드 도메인 (예: api.example.com)"
  type        = string
}

variable "grafana_domain" {
  description = "Grafana 도메인 (예: grafana.example.com)"
  type        = string
}

variable "monitoring_ip" {
  description = "모니터링 서버 퍼블릭 IP (Grafana proxy host forward 대상)"
  type        = string
}
