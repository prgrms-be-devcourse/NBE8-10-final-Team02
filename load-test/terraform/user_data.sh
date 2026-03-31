#!/bin/bash
set -euo pipefail
exec > /var/log/user_data.log 2>&1

echo "=== [1/5] 시스템 업데이트 ==="
dnf update -y

echo "=== [2/5] Docker 설치 ==="
dnf install -y docker
systemctl enable docker
systemctl start docker

# docker compose v2 플러그인
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
     -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

echo "=== [3/5] 앱 설정 파일 생성 ==="
mkdir -p /opt/load-test

# docker-compose 파일 생성 (templatefile 치환값 사용)
cat > /opt/load-test/docker-compose.yml << 'COMPOSE_EOF'
version: '3.8'

services:
  db:
    image: postgres:15-alpine
    container_name: lt-db
    command: >
      postgres
      -c shared_buffers=96MB
      -c max_connections=50
      -c work_mem=4MB
    environment:
      POSTGRES_USER: user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: appdb
    volumes:
      - lt-db-data:/var/lib/postgresql/data
    networks:
      - lt-net
    deploy:
      resources:
        limits:
          memory: 256M
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user -d appdb"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    container_name: lt-redis
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --maxmemory 100mb
      --maxmemory-policy volatile-lru
    networks:
      - lt-net
    deploy:
      resources:
        limits:
          memory: 128M
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  app:
    image: ${APP_IMAGE}
    container_name: lt-app
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      JAVA_OPTS: "-Xms256m -Xmx400m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
      # DB
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/appdb
      SPRING_DATASOURCE_USERNAME: user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 10
      SPRING_JPA_HIBERNATE_DDL_AUTO: update
      SPRING_JPA_SHOW_SQL: "false"
      # Redis
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      # JWT
      SECURITY_JWT_SECRET: ${JWT_SECRET}
      SECURITY_JWT_ACCESS_TTL_SECONDS: 900
      SECURITY_JWT_REFRESH_TTL_SECONDS: 86400
      SECURITY_COOKIE_SECURE: "false"
      # 부하테스트 스텁 모드 키 (빈 문자열이면 기능 비활성화)
      APP_LOAD_TEST_KEY: ${LOAD_TEST_KEY}
      # AI - 스텁 모드 전환 전까지는 실제 키 없이 실패 허용
      GEMINI_API_KEY: "load-test-placeholder"
      AI_PROVIDER: gemini
      # GitHub 레포 분석 기능 (미테스트 시 빈 문자열)
      KNOWLEDGE_GITHUB_TOKEN: ${KNOWLEDGE_GITHUB_TOKEN}
    networks:
      - lt-net
    deploy:
      resources:
        limits:
          memory: 512M
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 20s
      timeout: 5s
      retries: 6
      start_period: 60s
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped

networks:
  lt-net:

volumes:
  lt-db-data:
COMPOSE_EOF

echo "=== [4/5] 환경변수 파일 생성 ==="
cat > /opt/load-test/.env << ENV_EOF
APP_IMAGE=${app_image}
DB_PASSWORD=${db_password}
REDIS_PASSWORD=${redis_password}
LOAD_TEST_KEY=${load_test_key}
JWT_SECRET=${jwt_secret}
KNOWLEDGE_GITHUB_TOKEN=${knowledge_github_token}
ENV_EOF
chmod 600 /opt/load-test/.env

echo "=== [5/5] 컨테이너 기동 ==="
cd /opt/load-test
docker compose --env-file .env up -d

echo "=== 헬스체크 대기 (최대 120초) ==="
for i in $(seq 1 24); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "앱 기동 완료 (${i}번째 시도)"
    break
  fi
  echo "대기 중... (${i}/24)"
  sleep 5
done

echo "=== 부팅 완료 ==="
