#!/bin/bash
# ── k6 부하 테스트 실행 래퍼 ─────────────────────────────────────────────
# 사용법:
#   VUS=50 BASE_URL=http://<IP>:8080 ./run.sh ramp-up
#   VUS=30 DURATION=3m BASE_URL=http://<IP>:8080 ./run.sh constant
#   VUS=100 BASE_URL=http://<IP>:8080 ./run.sh spike
#
# 환경변수:
#   VUS              - 최대 가상 사용자 수 (기본: 10)
#   BASE_URL         - 대상 서버 URL (기본: http://localhost:8080)
#   DURATION         - constant 시나리오 유지 시간 (기본: 2m)
#   TEST_JWT_TOKEN   - 인증이 필요한 API 테스트 시 미리 발급한 JWT
#   LOAD_TEST_KEY    - 스텁 모드 전환 키
#   K6_OUT           - 결과 출력 형식 (예: json=result.json)
#   PROMETHEUS_URL   - Prometheus remote write URL (예: http://<OCI_IP>:9090/api/v1/write)
#                      설정 시 --out experimental-prometheus-rw 자동 추가
set -euo pipefail

SCENARIO="${1:-ramp-up}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# .env 파일이 있으면 로드 (git 추적 안 함)
if [ -f "$SCRIPT_DIR/.env" ]; then
  # shellcheck disable=SC1091
  set -a; source "$SCRIPT_DIR/.env"; set +a
fi

VUS="${VUS:-10}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION="${DURATION:-2m}"
TEST_JWT_TOKEN="${TEST_JWT_TOKEN:-}"
LOAD_TEST_KEY="${LOAD_TEST_KEY:-}"
K6_OUT="${K6_OUT:-}"
PROMETHEUS_URL="${PROMETHEUS_URL:-}"

# ── 시나리오 파일 선택 ────────────────────────────────────────────────────
case "$SCENARIO" in
  ramp-up|rampup)
    SCRIPT="$SCRIPT_DIR/scenarios/ramp-up.js"
    ;;
  constant|const)
    SCRIPT="$SCRIPT_DIR/scenarios/constant-vus.js"
    ;;
  spike)
    SCRIPT="$SCRIPT_DIR/scenarios/spike.js"
    ;;
  github-analysis|github)
    SCRIPT="$SCRIPT_DIR/scenarios/github-analysis.js"
    ;;
  document-upload|upload)
    SCRIPT="$SCRIPT_DIR/scenarios/document-upload.js"
    ;;
  interview-session|interview)
    SCRIPT="$SCRIPT_DIR/scenarios/interview-session.js"
    ;;
  *)
    echo "알 수 없는 시나리오: $SCENARIO"
    echo "사용 가능: ramp-up | constant | spike | github-analysis | document-upload | interview-session"
    exit 1
    ;;
esac

# ── k6 설치 확인 ──────────────────────────────────────────────────────────
if ! command -v k6 &> /dev/null; then
  echo "[ERROR] k6가 설치되지 않았습니다."
  echo "  macOS:  brew install k6"
  echo "  Linux:  https://grafana.com/docs/k6/latest/set-up/install-k6/"
  exit 1
fi

# ── 서버 상태 사전 확인 ────────────────────────────────────────────────────
echo "=== 서버 연결 확인: $BASE_URL/actuator/health ==="
if ! curl -sf "$BASE_URL/actuator/health" > /dev/null; then
  echo "[ERROR] 서버에 연결할 수 없습니다: $BASE_URL"
  exit 1
fi
echo "서버 정상 확인"

# ── 실행 ────────────────────────────────────────────────────────────────────
echo "=== 시나리오: $SCENARIO | VUS: $VUS | URL: $BASE_URL ==="

K6_ARGS=(
  run
  -e "VUS=$VUS"
  -e "BASE_URL=$BASE_URL"
  -e "DURATION=$DURATION"
)

if [ -n "$TEST_JWT_TOKEN" ]; then
  K6_ARGS+=(-e "TEST_JWT_TOKEN=$TEST_JWT_TOKEN")
fi

if [ -n "$LOAD_TEST_KEY" ]; then
  K6_ARGS+=(-e "LOAD_TEST_KEY=$LOAD_TEST_KEY")
fi

if [ -n "$K6_OUT" ]; then
  K6_ARGS+=(--out "$K6_OUT")
fi

if [ -n "$PROMETHEUS_URL" ]; then
  K6_ARGS+=(--out experimental-prometheus-rw)
  export K6_PROMETHEUS_RW_SERVER_URL="$PROMETHEUS_URL"
  export K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true
  echo "=== Prometheus remote write: $PROMETHEUS_URL ==="
fi

K6_ARGS+=("$SCRIPT")

k6 "${K6_ARGS[@]}"
