# 부하 테스트 (k6 + Terraform)

## 디렉터리 구조

```
load-test/
  terraform/          # AWS t3.micro 프로비저닝
    main.tf
    variables.tf
    outputs.tf
    user_data.sh      # EC2 부팅 시 Docker 설치 + 앱 기동
  k6/
    scenarios/
      ramp-up.js      # VU 1→MAX 단계적 증가 (메인)
      constant-vus.js # 고정 VU 유지
      spike.js        # 순간 폭발
    lib/
      endpoints.js    # API 경로 모음
      auth.js         # 토큰 헬퍼
      checks.js       # 공통 응답 검증 + 커스텀 메트릭
    run.sh            # 실행 래퍼
```

---

## 원터치 플로우

### 1. 사전 준비

```bash
# Terraform 설치: https://developer.hashicorp.com/terraform/install
# k6 설치
brew install k6           # macOS
# 또는 https://grafana.com/docs/k6/latest/set-up/install-k6/

# AWS CLI 자격증명 설정
aws configure
```

### 2. 인프라 프로비저닝

```bash
cd load-test/terraform
terraform init

# 기본값으로 실행 (t3.micro, ap-northeast-2)
terraform apply \
  -var="app_image=ghcr.io/prgrms-be-devcourse/nbe8-10-final-team02-backend:latest" \
  -var="load_test_key=my-secret-key"

# EC2 IP 확인
EC2_IP=$(terraform output -raw public_ip)
echo "앱 URL: http://$EC2_IP:8080"
```

### 3. 앱 기동 확인

```bash
curl http://$EC2_IP:8080/actuator/health
# {"status":"UP"} 확인
```

### 4. 부하 테스트 실행

```bash
cd load-test/k6

# 기본 (VU 10, ramp-up)
BASE_URL=http://$EC2_IP:8080 ./run.sh ramp-up

# VU 50으로 ramp-up
VUS=50 BASE_URL=http://$EC2_IP:8080 ./run.sh ramp-up

# VU 100 고정 3분 유지
VUS=100 DURATION=3m BASE_URL=http://$EC2_IP:8080 ./run.sh constant

# 스파이크
VUS=100 BASE_URL=http://$EC2_IP:8080 ./run.sh spike

# 인증이 필요한 API 포함 시 JWT 토큰 추가
VUS=30 BASE_URL=http://$EC2_IP:8080 TEST_JWT_TOKEN=<jwt> ./run.sh ramp-up

# 결과를 JSON으로 저장
VUS=50 BASE_URL=http://$EC2_IP:8080 K6_OUT=json=result.json ./run.sh ramp-up
```

### 5. (AI 스텁 준비 후) 스텁 모드 전환

```bash
# 앱 서버에서 직접 or 로컬에서 EC2로
curl -X POST http://$EC2_IP:8080/internal/load-test/enable \
     -H "X-Load-Test-Key: my-secret-key"

# 스텁 모드 확인
curl http://$EC2_IP:8080/internal/load-test/status

# 테스트 후 원래 모드로 복귀
curl -X POST http://$EC2_IP:8080/internal/load-test/disable \
     -H "X-Load-Test-Key: my-secret-key"
```

### 6. 인프라 삭제

```bash
cd load-test/terraform
terraform destroy
```

---

## 성능 목표 (docs/non-functional.md 기준)

| 엔드포인트 유형 | p95 목표 |
|----------------|---------|
| 단순 조회 | 1,000ms |
| 저장/수정/삭제 | 2,000ms |
| AI 생성 (스텁 적용 시) | 500ms |
| 헬스체크 | 500ms |

---

## 현재 활성 시나리오 / 대기 중

| 엔드포인트 | 상태 |
|-----------|------|
| `GET /actuator/health` | 활성 |
| `GET /api/v1/interview/cs-questions` | 활성 |
| `GET /api/v1/users/me` | 활성 (JWT 필요) |
| `POST /api/v1/applications` | 주석처리 (활성화 가능) |
| `POST .../cover-letters/generate` | **FakeAiClient 구현 후 활성화** |
| `POST .../interview-question-sets/generate` | **FakeAiClient 구현 후 활성화** |

---

## 테스트용 JWT 토큰 발급 방법

OAuth2 흐름은 k6로 자동화가 어려우므로, 아래 방법 중 하나를 사용한다.

**방법 A (권장)**: 개발 환경에서 로그인 후 쿠키/응답에서 토큰 추출 → 환경변수로 주입
```bash
export TEST_JWT_TOKEN="eyJhbG..."
```

**방법 B**: Spring Boot에 `/internal/load-test/token` 엔드포인트 추가
- `X-Load-Test-Key` 헤더 검증 후 테스트용 사용자 JWT 발급
- 현재 미구현 → `auth.js`의 TODO 참고

---

## t3.micro 메모리 구성

```
총 1GB
├── JVM (Spring Boot)  : Xmx400m
├── PostgreSQL         : shared_buffers=96MB (~256MB 전체)
├── Redis              : maxmemory=100MB (~128MB 전체)
├── OS + Docker        : ~150MB
└── 여유                : ~70MB
```

> VU 100 이상으로 k6를 EC2 외부에서 실행 권장.
> EC2 내부에서 k6 실행 시 k6 자체가 메모리 약 150MB 추가 소모.
