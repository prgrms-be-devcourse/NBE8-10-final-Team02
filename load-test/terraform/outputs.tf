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
  value       = "ssh -i ${var.ssh_public_key_path} ec2-user@${aws_instance.load_test.public_ip}"
}

output "health_check_url" {
  description = "헬스체크 URL"
  value       = "http://${aws_instance.load_test.public_ip}:8080/actuator/health"
}
