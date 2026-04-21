data "oci_core_images" "ubuntu" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "22.04"
  shape                    = var.instance_shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

# ── VCN ────────────────────────────────────────────────────────────────────
resource "oci_core_vcn" "monitoring" {
  compartment_id = var.compartment_ocid
  cidr_blocks    = ["10.1.0.0/16"]
  display_name   = "${var.project_name}-vcn"
  dns_label      = "monitoringvcn"
}

resource "oci_core_internet_gateway" "monitoring" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.monitoring.id
  display_name   = "${var.project_name}-igw"
  enabled        = true
}

resource "oci_core_route_table" "monitoring" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.monitoring.id
  display_name   = "${var.project_name}-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.monitoring.id
  }
}

# ── 보안 규칙 ─────────────────────────────────────────────────────────────────
resource "oci_core_security_list" "monitoring" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.monitoring.id
  display_name   = "${var.project_name}-sl"

  ingress_security_rules {
    description = "SSH"
    protocol    = "6"
    source      = var.admin_cidr
    tcp_options {
      min = 22
      max = 22
    }
  }

  ingress_security_rules {
    description = "Prometheus UI"
    protocol    = "6"
    source      = var.admin_cidr
    tcp_options {
      min = 9090
      max = 9090
    }
  }

  ingress_security_rules {
    description = "Grafana UI"
    protocol    = "6"
    source      = var.admin_cidr
    tcp_options {
      min = 3001
      max = 3001
    }
  }

  ingress_security_rules {
    description = "Loki (주 서버에서만)"
    protocol    = "6"
    source      = "${var.app_server_ip}/32"
    tcp_options {
      min = 3100
      max = 3100
    }
  }

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }
}

resource "oci_core_subnet" "monitoring" {
  compartment_id    = var.compartment_ocid
  vcn_id            = oci_core_vcn.monitoring.id
  cidr_block        = "10.1.1.0/24"
  display_name      = "${var.project_name}-subnet"
  dns_label         = "monitoring"
  route_table_id    = oci_core_route_table.monitoring.id
  security_list_ids = [oci_core_security_list.monitoring.id]
}

# ── 인스턴스 ─────────────────────────────────────────────────────────────────
resource "oci_core_instance" "monitoring" {
  compartment_id      = var.compartment_ocid
  availability_domain = var.availability_domain
  display_name        = "${var.project_name}-app"
  shape               = var.instance_shape

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu.images[0].id
    boot_volume_size_in_gbs = var.boot_volume_size_gb
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.monitoring.id
    assign_public_ip = true
    display_name     = "${var.project_name}-vnic"
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data = base64encode(templatefile("${path.module}/user_data.sh.tpl", {
      project_dir = var.project_dir
      repo_url    = var.repo_url
    }))
  }

  freeform_tags = {
    Project = var.project_name
  }
}

# ── 주 서버 promtail-config.yml Loki URL 자동 업데이트 ─────────────────────────
resource "null_resource" "update_promtail" {
  depends_on = [oci_core_instance.monitoring]

  triggers = {
    monitoring_ip = oci_core_instance.monitoring.public_ip
  }

  connection {
    type        = "ssh"
    host        = var.app_server_ip
    user        = "ubuntu"
    private_key = file(pathexpand(var.app_server_ssh_private_key_path))
  }

  provisioner "remote-exec" {
    inline = [
      "sed -i 's|http://[^:]*:3100|http://${oci_core_instance.monitoring.public_ip}:3100|g' ${var.app_server_project_dir}/docker/promtail-config.yml",
      "sudo docker restart prod-promtail",
      "echo '✅ promtail → Loki: ${oci_core_instance.monitoring.public_ip}:3100'"
    ]
  }
}
