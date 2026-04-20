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

variable "compartment_ocid" {
  type = string
}

variable "vcn_ocid" {
  type = string
}

variable "admin_cidr" {
  type    = string
  default = "0.0.0.0/0"
}

variable "extra_ingress_ports" {
  type    = list(number)
  default = []
}
