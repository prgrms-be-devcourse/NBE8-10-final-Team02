output "security_list_ocid" {
  description = "생성된 Security List OCID — OCI 콘솔에서 서브넷에 최초 1회 연결 필요"
  value       = oci_core_security_list.service.id
}

output "next_steps" {
  value = <<-EOT
    [최초 1회] OCI 콘솔 → Networking → VCN → 서브넷 → Security Lists
    → 기존 규칙 삭제 → 위 security_list_ocid로 연결

    [매번] main 브랜치 push → GitHub Actions Blue/Green 배포
    [규칙 변경] extra_ingress_ports 수정 후 terraform apply
  EOT
}
