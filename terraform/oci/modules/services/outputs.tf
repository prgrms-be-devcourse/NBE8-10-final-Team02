output "next_steps" {
  description = "배포 후 수동 작업"
  value       = <<-EOT
    1. GitHub Actions 실행:
       main 브랜치 push → Blue/Green 자동 배포
  EOT
}
