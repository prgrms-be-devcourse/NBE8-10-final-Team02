# ── 보안 규칙: 서비스에 맞게 교체 ─────────────────────────────────────────────
resource "oci_core_security_list" "service" {
  compartment_id = var.compartment_ocid
  vcn_id         = var.vcn_ocid
  display_name   = "service-security-list"

  ingress_security_rules {
    description = "SSH"
    protocol    = "6"
    source      = var.admin_cidr
    tcp_options { min = 22; max = 22 }
  }

  ingress_security_rules {
    description = "HTTP"
    protocol    = "6"
    source      = "0.0.0.0/0"
    tcp_options { min = 80; max = 80 }
  }

  ingress_security_rules {
    description = "HTTPS"
    protocol    = "6"
    source      = "0.0.0.0/0"
    tcp_options { min = 443; max = 443 }
  }

  ingress_security_rules {
    description = "NPM UI"
    protocol    = "6"
    source      = var.admin_cidr
    tcp_options { min = 81; max = 81 }
  }

  dynamic "ingress_security_rules" {
    for_each = var.extra_ingress_ports
    content {
      description = "extra port ${ingress_security_rules.value}"
      protocol    = "6"
      source      = "0.0.0.0/0"
      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }
}

# ── Step 1: 디렉터리 생성 ──────────────────────────────────────────────────────
resource "null_resource" "prepare" {
  triggers = {
    repo_url = var.repo_url
  }

  connection {
    type        = "ssh"
    host        = var.server_ip
    user        = "ubuntu"
    private_key = file(pathexpand(var.ssh_private_key_path))
  }

  provisioner "remote-exec" {
    inline = [
      "sudo mkdir -p /home/ubuntu/secrets /data/repos /app/uploads ${var.project_dir}",
      "sudo chown -R ubuntu:ubuntu /home/ubuntu/secrets /data/repos /app/uploads ${var.project_dir}",
    ]
  }
}

# ── Step 2: 시크릿 파일 업로드 ────────────────────────────────────────────────
resource "null_resource" "upload_secrets" {
  depends_on = [null_resource.prepare]

  triggers = {
    repo_url = var.repo_url
  }

  connection {
    type        = "ssh"
    host        = var.server_ip
    user        = "ubuntu"
    private_key = file(pathexpand(var.ssh_private_key_path))
  }

  provisioner "file" {
    source      = pathexpand(var.vertex_ai_key_path)
    destination = "/home/ubuntu/secrets/vertex-ai-key.json"
  }
}

# ── Step 3: 초기화 → 새 서비스 기동 ──────────────────────────────────────────
resource "null_resource" "deploy" {
  depends_on = [null_resource.upload_secrets]

  triggers = {
    repo_url = var.repo_url
  }

  connection {
    type        = "ssh"
    host        = var.server_ip
    user        = "ubuntu"
    private_key = file(pathexpand(var.ssh_private_key_path))
  }

  provisioner "remote-exec" {
    inline = [
      "docker rm -f $(docker ps -aq) 2>/dev/null || true",
      "docker volume prune -f",
      "docker image prune -af",
      "sudo rm -rf /data/repos/* /app/uploads/*",
      "rm -rf ${var.project_dir}/repo ${var.project_dir}/docker-compose.prod.yml",
      "docker network create global-net 2>/dev/null || true",
      "git clone ${var.repo_url} ${var.project_dir}/repo",
      "cp ${var.project_dir}/repo/docker-compose.prod.yml ${var.project_dir}/docker-compose.prod.yml",
      "cp -rn ${var.project_dir}/repo/docker/ ${var.project_dir}/docker/ 2>/dev/null || true",
      "cd ${var.project_dir} && docker compose -f docker-compose.prod.yml up -d db redis node-exporter postgres-exporter promtail",
      "echo '✅ 주 서버 서비스 기동 완료'"
    ]
  }

  provisioner "remote-exec" {
    when = destroy
    inline = [
      "docker compose -f ${var.project_dir}/docker-compose.prod.yml down 2>/dev/null || true",
      "docker volume prune -f",
      "echo '✅ 주 서버 서비스 중단 완료. NPM·SSL 설정은 유지됩니다.'"
    ]
  }
}
