variable "compartment_ocid" {
  type = string
}

variable "availability_domain" {
  type = string
}

variable "project_name" {
  type    = string
  default = "devcos-team2-monitoring"
}

variable "project_dir" {
  type    = string
  default = "/home/ubuntu/monitoring"
}

variable "repo_url" {
  type    = string
  default = "https://github.com/prgrms-be-devcourse/NBE8-10-final-Team02.git"
}

variable "instance_shape" {
  type    = string
  default = "VM.Standard.E2.1.Micro"
}

variable "boot_volume_size_gb" {
  type    = number
  default = 50
}

variable "ssh_public_key" {
  type = string
}

variable "admin_cidr" {
  type    = string
  default = "0.0.0.0/0"
}

variable "app_server_ip" {
  type = string
}

variable "app_server_ssh_private_key_path" {
  type    = string
  default = "~/.ssh/id_rsa"
}

variable "app_server_project_dir" {
  type    = string
  default = "/home/ubuntu/my-project"
}
