output "public_ip" {
  description = "EC2 퍼블릭 IP"
  value       = aws_instance.load_test.public_ip
}

output "public_dns" {
  description = "EC2 퍼블릭 DNS"
  value       = aws_instance.load_test.public_dns
}

output "app_url" {
  description = "애플리케이션 베이스 URL"
  value       = "http://${aws_instance.load_test.public_ip}:8080"
}

output "ssh_command" {
  description = "SSH 접속 명령"
  value       = "ssh -i ${replace(var.ssh_public_key_path, ".pub", "")} ec2-user@${aws_instance.load_test.public_ip}"
}

output "health_check_url" {
  description = "헬스체크 URL"
  value       = "http://${aws_instance.load_test.public_ip}:8080/actuator/health"
}

output "grafana_url" {
  description = "Grafana 대시보드 URL (admin/admin)"
  value       = "http://${aws_instance.load_test.public_ip}:3000"
}

output "prometheus_url" {
  description = "Prometheus URL"
  value       = "http://${aws_instance.load_test.public_ip}:9090"
}
