terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}

# ── 보안 규칙 (null_resource: destroy 시 OCI API 호출 없이 state만 제거됨) ────
locals {
  base_ingress_rules = [
    { description = "SSH",     protocol = "6", source = var.admin_cidr, sourceType = "CIDR_BLOCK", isStateless = false, tcpOptions = { destinationPortRange = { min = 22,  max = 22  } } },
    { description = "HTTP",    protocol = "6", source = "0.0.0.0/0",    sourceType = "CIDR_BLOCK", isStateless = false, tcpOptions = { destinationPortRange = { min = 80,  max = 80  } } },
    { description = "HTTPS",   protocol = "6", source = "0.0.0.0/0",    sourceType = "CIDR_BLOCK", isStateless = false, tcpOptions = { destinationPortRange = { min = 443, max = 443 } } },
    { description = "NPM UI",  protocol = "6", source = var.admin_cidr, sourceType = "CIDR_BLOCK", isStateless = false, tcpOptions = { destinationPortRange = { min = 81,  max = 81  } } },
  ]
  extra_ingress_rules = [for p in var.extra_ingress_ports : {
    description = "extra port ${p}", protocol = "6", source = "0.0.0.0/0", sourceType = "CIDR_BLOCK", isStateless = false, tcpOptions = { destinationPortRange = { min = p, max = p } }
  }]
  egress_rules = [
    { destination = "0.0.0.0/0", destinationType = "CIDR_BLOCK", protocol = "all", isStateless = false }
  ]
  all_ingress_rules = jsonencode(concat(local.base_ingress_rules, local.extra_ingress_rules))
  all_egress_rules  = jsonencode(local.egress_rules)
}

resource "null_resource" "security_rules" {
  triggers = {
    security_list_id = var.default_security_list_ocid
    ingress_rules    = local.all_ingress_rules
    egress_rules     = local.all_egress_rules
  }

  provisioner "local-exec" {
    interpreter = ["bash", "-c"]
    command = <<-EOT
      oci network security-list update \
        --security-list-id "${var.default_security_list_ocid}" \
        --ingress-security-rules '${local.all_ingress_rules}' \
        --egress-security-rules '${local.all_egress_rules}' \
        --force
    EOT
  }
}

# ── Step 1: 디렉터리 생성 + OS 포트 개방 (iptables) ───────────────────────────
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

      # Docker 미설치 시 설치 (Rebuild instance 후 재적용 대응)
      "if ! command -v docker &>/dev/null; then curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu && echo '✅ Docker 설치 완료'; fi",

      # OCI Ubuntu 기본 iptables 규칙에 포트 추가
      "sudo apt-get install -y iptables-persistent 2>/dev/null || true",
      "for port in 22 80 443 81 ${join(" ", var.extra_ingress_ports)}; do sudo iptables -C INPUT -m state --state NEW -p tcp --dport $port -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport $port -j ACCEPT; done",
      "sudo netfilter-persistent save 2>/dev/null || true",
    ]
  }
}

# ── Step 2: 파일 업로드 (시크릿 + 서비스 설정) ────────────────────────────────
resource "null_resource" "upload_secrets" {
  depends_on = [null_resource.prepare]

  triggers = {
    docker_compose_hash = filemd5("${path.root}/../../docker-compose.prod.yml")
    vertex_ai_key_path  = var.vertex_ai_key_path
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

  provisioner "file" {
    source      = "${path.root}/../../docker-compose.prod.yml"
    destination = "${var.project_dir}/docker-compose.prod.yml"
  }

  provisioner "file" {
    source      = "${path.root}/../../docker"
    destination = var.project_dir
  }
}

# ── Step 3: 초기화 → 새 서비스 기동 ──────────────────────────────────────────
resource "null_resource" "deploy" {
  depends_on = [null_resource.upload_secrets]

  triggers = {
    repo_url        = var.repo_url
    server_ip       = var.server_ip
    ssh_private_key = file(pathexpand(var.ssh_private_key_path))
    project_dir     = var.project_dir
  }

  provisioner "remote-exec" {
    connection {
      type        = "ssh"
      host        = var.server_ip
      user        = "ubuntu"
      private_key = file(pathexpand(var.ssh_private_key_path))
    }
    inline = [
      "sudo docker rm -f $(sudo docker ps -aq) 2>/dev/null || true",
      "sudo docker volume prune -f",
      "sudo docker image prune -af",
      "sudo rm -rf /data/repos/* /app/uploads/*",
      "sudo docker network create global-net 2>/dev/null || true",
      "sudo rm -f ${var.project_dir}/docker/npm/data/database.sqlite",
      "cd ${var.project_dir} && sudo docker compose -f docker-compose.prod.yml up -d npm db redis node-exporter postgres-exporter promtail",
      "echo '✅ 주 서버 서비스 기동 완료'"
    ]
  }

  provisioner "remote-exec" {
    when = destroy
    connection {
      type        = "ssh"
      host        = self.triggers.server_ip
      user        = "ubuntu"
      private_key = self.triggers.ssh_private_key
    }
    inline = [
      "sudo docker compose -f ${self.triggers.project_dir}/docker-compose.prod.yml down 2>/dev/null || true",
      "sudo docker volume prune -f",
      "echo '✅ 주 서버 서비스 중단 완료. NPM SSL 설정은 유지됩니다.'"
    ]
  }
}

# ── Step 4: NPM proxy host 자동 설정 ──────────────────────────────────────────
resource "null_resource" "npm_setup" {
  depends_on = [null_resource.deploy]

  triggers = {
    repo_url   = var.repo_url
    fe_domain  = var.fe_domain
    be_domain  = var.be_domain
  }

  connection {
    type        = "ssh"
    host        = var.server_ip
    user        = "ubuntu"
    private_key = file(pathexpand(var.ssh_private_key_path))
  }

  provisioner "remote-exec" {
    inline = [
      # NPM 기동 대기
      "echo '⏳ NPM 기동 대기...'",
      "for i in $(seq 1 24); do curl -sf http://localhost:81/api/ >/dev/null 2>&1 && break || sleep 5; done",

      # 토큰 발급
      "TOKEN=$(curl -sf -X POST http://localhost:81/api/tokens -H 'Content-Type: application/json' -d '{\"identity\":\"${var.npm_email}\",\"secret\":\"${var.npm_password}\"}' | jq -r .token)",
      "[[ -z \"$TOKEN\" || \"$TOKEN\" == \"null\" ]] && { echo '❌ NPM 로그인 실패. npm_email/npm_password 확인'; exit 1; }",

      # BE proxy host 생성 or 업데이트
      "BE_ID=$(curl -sf -H \"Authorization: Bearer $TOKEN\" http://localhost:81/api/nginx/proxy-hosts | jq -r '.[] | select(.domain_names[] == \"${var.be_domain}\") | .id')",
      "BE_BODY=$(jq -n --arg d '${var.be_domain}' --arg h 'be_a' '{domain_names:[$d],forward_scheme:\"http\",forward_host:$h,forward_port:8080,access_list_id:0,certificate_id:0,ssl_forced:false,caching_enabled:false,block_exploits:true,allow_websocket_upgrade:true,advanced_config:\"\",locations:[],meta:{}}')",
      "if [ -z \"$BE_ID\" ]; then curl -sf -o /dev/null -X POST -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' -d \"$BE_BODY\" http://localhost:81/api/nginx/proxy-hosts; else curl -sf -o /dev/null -X PUT -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' -d \"$BE_BODY\" http://localhost:81/api/nginx/proxy-hosts/$BE_ID; fi",

      # FE proxy host 생성 or 업데이트
      "FE_ID=$(curl -sf -H \"Authorization: Bearer $TOKEN\" http://localhost:81/api/nginx/proxy-hosts | jq -r '.[] | select(.domain_names[] == \"${var.fe_domain}\") | .id')",
      "FE_BODY=$(jq -n --arg d '${var.fe_domain}' --arg h 'fe_a' '{domain_names:[$d],forward_scheme:\"http\",forward_host:$h,forward_port:3000,access_list_id:0,certificate_id:0,ssl_forced:false,caching_enabled:false,block_exploits:true,allow_websocket_upgrade:true,advanced_config:\"\",locations:[],meta:{}}')",
      "if [ -z \"$FE_ID\" ]; then curl -sf -o /dev/null -X POST -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' -d \"$FE_BODY\" http://localhost:81/api/nginx/proxy-hosts; else curl -sf -o /dev/null -X PUT -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' -d \"$FE_BODY\" http://localhost:81/api/nginx/proxy-hosts/$FE_ID; fi",

      # Grafana proxy host 생성 or 업데이트 (모니터링 서버 IP로 포워딩)
      "GF_ID=$(curl -sf -H \"Authorization: Bearer $TOKEN\" http://localhost:81/api/nginx/proxy-hosts | jq -r '.[] | select(.domain_names[] == \"${var.grafana_domain}\") | .id')",
      "GF_BODY=$(jq -n --arg d '${var.grafana_domain}' --arg h '${var.monitoring_ip}' '{domain_names:[$d],forward_scheme:\"http\",forward_host:$h,forward_port:3001,access_list_id:0,certificate_id:0,ssl_forced:false,caching_enabled:false,block_exploits:true,allow_websocket_upgrade:false,advanced_config:\"\",locations:[],meta:{}}')",
      "if [ -z \"$GF_ID\" ]; then curl -sf -o /dev/null -X POST -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' -d \"$GF_BODY\" http://localhost:81/api/nginx/proxy-hosts; else curl -sf -o /dev/null -X PUT -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' -d \"$GF_BODY\" http://localhost:81/api/nginx/proxy-hosts/$GF_ID; fi",

      "echo '✅ NPM proxy host 설정 완료 (SSL은 NPM UI에서 최초 1회 설정)'"
    ]
  }
}

# ── Step 5: 인스턴스 Rebuild (destroy 시만 실행) ─────────────────────────────────
# Replace Boot Volume API를 사용해 OCI 백엔드가 한 번에 처리:
# 이미지 OCID 추출 → STOP → 구형 볼륨 OCID 백업 → 볼륨 교체 → START → 구형 볼륨 삭제
resource "null_resource" "instance_rebuild" {
  depends_on = [null_resource.npm_setup]

  triggers = {
    instance_ocid    = var.instance_ocid
    compartment_ocid = var.compartment_ocid
  }

  provisioner "local-exec" {
    when        = destroy
    interpreter = ["bash", "-c"]
    command     = <<-EOT
      set -e
      echo "🔄 주 서버 인스턴스 Rebuild 시작..."

      # 1. 현재 인스턴스의 소스 이미지 OCID 추출
      SOURCE_IMAGE_ID=$(oci compute instance get \
        --instance-id "${self.triggers.instance_ocid}" \
        --query 'data."source-details"."image-id"' \
        --raw-output)

      if [ -z "$SOURCE_IMAGE_ID" ] || [ "$SOURCE_IMAGE_ID" == "null" ]; then
        echo "❌ 오류: 소스 이미지 ID를 가져오지 못했습니다. Rebuild를 중단합니다."
        exit 1
      fi

      echo "✅ 소스 이미지 OCID: $SOURCE_IMAGE_ID"

      # 2. 인스턴스 중지 (부트 볼륨 교체는 STOPPED 상태에서만 가능)
      oci compute instance action \
        --instance-id "${self.triggers.instance_ocid}" \
        --action STOP \
        --wait-for-state STOPPED
      echo "✅ 인스턴스 중지 완료"

      # 3. 기존 부트 볼륨 OCID 백업 (뒷정리용)
      OLD_BOOT_VOLUME_ID=$(oci compute boot-volume-attachment list \
        --instance-id "${self.triggers.instance_ocid}" \
        --compartment-id "${self.triggers.compartment_ocid}" \
        --query 'data[0]."boot-volume-id"' \
        --raw-output)
      echo "✅ 기존 부트 볼륨 OCID: $OLD_BOOT_VOLUME_ID"

      # 4. 부트 볼륨 교체 (다형성 지원 전용 명령 사용 — instance update의 --source-details는 image 타입 미지원)
      oci compute instance update-instance-update-instance-source-via-image-details \
        --instance-id "${self.triggers.instance_ocid}" \
        --source-details-image-id "$SOURCE_IMAGE_ID" \
        --force \
        --wait-for-state STOPPED
      echo "✅ 부트 볼륨 교체 완료"

      # 5. 인스턴스 재시작
      oci compute instance action \
        --instance-id "${self.triggers.instance_ocid}" \
        --action START \
        --wait-for-state RUNNING
      echo "✅ 인스턴스 재시작 완료"

      # 6. 분리된 구형 부트 볼륨 삭제 (과금 방지)
      if [ -n "$OLD_BOOT_VOLUME_ID" ] && [ "$OLD_BOOT_VOLUME_ID" != "null" ]; then
        oci bv boot-volume delete \
          --boot-volume-id "$OLD_BOOT_VOLUME_ID" \
          --force
        echo "✅ 구형 부트 볼륨 삭제 완료"
      fi

      echo "✅ 주 서버 인스턴스 Rebuild 완료"
    EOT
  }
}
