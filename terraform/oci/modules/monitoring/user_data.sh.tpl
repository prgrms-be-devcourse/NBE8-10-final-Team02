#!/bin/bash
set -euo pipefail
exec > >(tee /var/log/user_data.log | logger -t user_data) 2>&1

echo "=== [1/4] Docker 설치 ==="
apt-get update -y
apt-get install -y ca-certificates curl gnupg git

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

echo "=== [2/4] 네트워크 및 디렉터리 생성 ==="
docker network create global-net || true
mkdir -p ${project_dir}
chown ubuntu:ubuntu ${project_dir}

echo "=== [3/4] 레포 클론 ==="
sudo -u ubuntu git clone ${repo_url} ${project_dir}/repo

cp ${project_dir}/repo/docker-compose.prod-monitoring.yml ${project_dir}/docker-compose.prod-monitoring.yml
cp -r ${project_dir}/repo/docker/ ${project_dir}/docker/ 2>/dev/null || true

echo "=== [4/4] 모니터링 서비스 기동 ==="
cd ${project_dir}
docker compose -f docker-compose.prod-monitoring.yml up -d

echo "=== 완료 ==="
