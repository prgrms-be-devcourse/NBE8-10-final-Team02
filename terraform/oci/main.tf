terraform {
  required_version = ">= 1.6"
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
  }
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

# ── 모니터링 서버 (destroy/apply로 완전 재생성) ────────────────────────────────
module "monitoring" {
  source = "./modules/monitoring"

  compartment_ocid    = var.compartment_ocid
  availability_domain = var.monitoring_availability_domain
  ssh_public_key      = var.ssh_public_key
  repo_url            = var.repo_url
  admin_cidr          = var.admin_cidr

  app_server_ip                   = var.app_server_ip
  ssh_private_key_path            = var.ssh_private_key_path
  app_server_ssh_private_key_path = var.ssh_private_key_path
  app_server_project_dir          = var.app_server_project_dir
}

# ── 주 서버 서비스 (기존 인스턴스 위에서 서비스만 관리) ──────────────────────────
# depends_on: apply 시 monitoring 먼저 생성 → services 기동
#             destroy 시 services 먼저 중단 → monitoring 삭제
module "services" {
  source     = "./modules/services"
  depends_on = [module.monitoring]

  server_ip            = var.app_server_ip
  ssh_private_key_path = var.ssh_private_key_path
  repo_url             = var.repo_url
  project_dir          = var.app_server_project_dir
  vertex_ai_key_path   = var.vertex_ai_key_path
  instance_ocid        = var.app_server_instance_ocid

  compartment_ocid = var.compartment_ocid
  vcn_ocid         = var.app_server_vcn_ocid
  npm_email        = var.npm_email
  npm_password     = var.npm_password
  fe_domain        = var.fe_domain
  be_domain        = var.be_domain
  grafana_domain   = var.grafana_domain
  monitoring_ip    = module.monitoring.public_ip
  admin_cidr          = var.admin_cidr
  extra_ingress_ports = var.extra_ingress_ports
}
