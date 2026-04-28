#!/bin/bash
# bench-compare.sh — 아키텍처 변경 전/후 부하테스트 자동 비교
#
# 사용법:
#   ./bench-compare.sh [build|test|report|all]
#
# 환경변수 (선택):
#   GHCR_TOKEN       — ghcr.io 인증 PAT (docker login; 이미 로그인되어 있으면 생략 가능)
#   K6_SCENARIO      — k6 시나리오 파일명 기준 (기본: ramp-up)
#                      ramp-up | constant-vus | spike | github-analysis | ...
#   K6_VUS           — 최대 VUS (기본: 30)
#   K6_DURATION      — constant-vus 유지 시간 (기본: 2m)
#   TEST_JWT_TOKEN   — 인증 필요 시나리오용 JWT
#   LOAD_TEST_KEY    — 스텁 모드 전환 키
#   KEEP_RAW         — 1이면 k6 raw JSON 보관 (기본: 삭제)
#
# 선행 조건:
#   docker, git, terraform, k6, jq, curl
#   AWS 자격증명 (terraform apply용)
#   ghcr.io 이미지 push 권한
#   Oracle Container Registry 로그인 (GraalVM 베이스 이미지용)
#     → docker login container-registry.oracle.com

set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════
# 설정
# ═══════════════════════════════════════════════════════════════════════════
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

GHCR_REPO="ghcr.io/prgrms-be-devcourse/nbe8-10-final-team02-backend"
TERRAFORM_DIR="$SCRIPT_DIR/terraform"
K6_DIR="$SCRIPT_DIR/k6"
RESULTS_DIR="$SCRIPT_DIR/results"
WORKTREE_BASE="${TMPDIR:-/tmp}/lt-bench-$$"

K6_SCENARIO="${K6_SCENARIO:-ramp-up}"
K6_VUS="${K6_VUS:-20}"
K6_DURATION="${K6_DURATION:-2m}"
TEST_JWT_TOKEN="${TEST_JWT_TOKEN:-}"
LOAD_TEST_KEY="${LOAD_TEST_KEY:-}"
KEEP_RAW="${KEEP_RAW:-0}"
GHCR_TOKEN="${GHCR_TOKEN:-}"
SSH_KEY_PATH="${SSH_KEY_PATH:-~/.ssh/my-key}"

# 비교 쌍 정의: "레이블:커밋ref:설명"
# before → after 순서 유지 (같은 쌍은 연속으로)
PAIRS=(
  "before-tx:b76d897~1:AI 트랜잭션 분리(이전)"
  "after-tx:b76d897:AI 트랜잭션 분리(이후)"
  "before-sema:8f66311~1:세마포어 도입(이전)"
  "after-sema:8f66311:세마포어 도입(이후)"
  "before-async:97110fd~1:비동기 전환(이전)"
  "after-async:97110fd:비동기 전환(이후)"
  "final-sema-2:2d6f0a5:최종 결과물(세마포어 2):2"
  "final-sema-20:2d6f0a5:최종 결과물(세마포어 20):20"
)

# ═══════════════════════════════════════════════════════════════════════════
# 유틸리티
# ═══════════════════════════════════════════════════════════════════════════
log()  { echo "[$(date '+%H:%M:%S')] $*"; }
step() { echo ""; echo "━━━ $* ━━━"; }
err()  { echo "[ERROR] $*" >&2; }

require_cmd() {
  local missing=()
  for cmd in "$@"; do
    command -v "$cmd" &>/dev/null || missing+=("$cmd")
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    err "필수 명령어가 없습니다: ${missing[*]}"
    exit 1
  fi
}

parse_pair() {
  # "label:commit:desc[:sema]" → label / commit / desc / sema(optional)
  IFS=: read -r PAIR_LABEL PAIR_COMMIT PAIR_DESC PAIR_SEMA <<< "$1"
}

# 임시 worktree 정리 (오류 시에도 실행)
ACTIVE_WORKTREES=()
cleanup() {
  for wt in "${ACTIVE_WORKTREES[@]:-}"; do
    [[ -d "$wt" ]] && git -C "$REPO_ROOT" worktree remove --force "$wt" 2>/dev/null || true
  done
  rm -rf "$WORKTREE_BASE"
}
trap cleanup EXIT
trap 'echo ""; err "스크립트 오류 — 라인 $LINENO, 종료코드 $?" >&2' ERR

# ═══════════════════════════════════════════════════════════════════════════
# Phase 1: Docker 이미지 빌드 & GHCR push
# ═══════════════════════════════════════════════════════════════════════════
ghcr_login() {
  if [[ -n "$GHCR_TOKEN" ]]; then
    log "ghcr.io 로그인 중 (GHCR_TOKEN)..."
    echo "$GHCR_TOKEN" | docker login ghcr.io -u x-access-token --password-stdin
  else
    log "GHCR_TOKEN 없음 — 기존 docker 로그인 상태 사용"
  fi
}

find_pair() {
  local target="$1"
  for pair in "${PAIRS[@]}"; do
    IFS=: read -r lbl _ _ <<< "$pair"
    if [[ "$lbl" == "$target" ]]; then
      echo "$pair"
      return 0
    fi
  done
  return 1
}

build_one() {
  local label="$1" commit="$2" desc="$3"
  local tag="$GHCR_REPO:bench-$label"
  local worktree="$WORKTREE_BASE/$label"

  log "[$label] $desc — 커밋: $(git -C "$REPO_ROOT" rev-parse --short "$commit")"

  mkdir -p "$WORKTREE_BASE"
  git -C "$REPO_ROOT" worktree add "$worktree" "$commit" --detach
  ACTIVE_WORKTREES+=("$worktree")

  # 세마포어 값 override (PAIR_SEMA가 있을 때만)
  if [[ -n "${PAIR_SEMA:-}" ]]; then
    local yml="$worktree/backend/src/main/resources/application-load-test.yml"
    sed -i "s/max-concurrent-calls: [0-9]*/max-concurrent-calls: $PAIR_SEMA/" "$yml"
    log "[$label] 세마포어 override → $PAIR_SEMA (application-load-test.yml)"
  fi

  log "[$label] docker build → $tag"
  docker build \
    --platform linux/amd64 \
    --file  "$worktree/backend/Dockerfile" \
    --tag   "$tag" \
    "$worktree/backend"

  log "[$label] docker push → $tag"
  docker push "$tag"

  git -C "$REPO_ROOT" worktree remove --force "$worktree"
  ACTIVE_WORKTREES=("${ACTIVE_WORKTREES[@]/$worktree/}")
  log "[$label] 빌드 완료 ✓"
}

do_build() {
  step "Phase 1: Docker 이미지 빌드 & Push (전체)"
  ghcr_login

  for pair in "${PAIRS[@]}"; do
    parse_pair "$pair"
    build_one "$PAIR_LABEL" "$PAIR_COMMIT" "$PAIR_DESC"
  done
}

do_build_one() {
  local target="$1"
  local pair
  pair=$(find_pair "$target") || { err "알 수 없는 레이블: $target (유효값: before-tx after-tx before-sema after-sema before-async after-async)"; exit 1; }
  parse_pair "$pair"

  step "Phase 1: Docker 이미지 빌드 & Push [$PAIR_LABEL]"
  ghcr_login
  build_one "$PAIR_LABEL" "$PAIR_COMMIT" "$PAIR_DESC"
}

# ═══════════════════════════════════════════════════════════════════════════
# Phase 2: EC2 배포 → k6 실행 → destroy
# ═══════════════════════════════════════════════════════════════════════════
run_one_test() {
  local label="$1" desc="$2"
  local tag="$GHCR_REPO:bench-$label"
  local scenario_suffix=""
  [[ "$K6_SCENARIO" != "constant-vus" ]] && scenario_suffix="-${K6_SCENARIO}"

  local instance_suffix=""
  local _itype
  _itype=$(grep -E 'default\s*=\s*"t[0-9]' "$TERRAFORM_DIR/variables.tf" | grep -o '"t[^"]*"' | tr -d '"' | head -1)
  case "$_itype" in
    t3.small|t4g.small) instance_suffix="" ;;
    *) instance_suffix="-$(echo "$_itype" | cut -d. -f2)" ;;
  esac

  local summary_file="$RESULTS_DIR/${label}-vus${K6_VUS}${scenario_suffix}${instance_suffix}-summary.json"
  local raw_file="$RESULTS_DIR/${label}-vus${K6_VUS}${scenario_suffix}${instance_suffix}-raw.json"

  log "[$label] 시작: $desc"

  # ── EC2 기동 or 이미지 교체 ──────────────────────────────────────────
  local ec2_ip
  ec2_ip="$(cd "$TERRAFORM_DIR" && terraform output -raw public_ip 2>/dev/null || true)"

  if [[ -z "$ec2_ip" ]]; then
    log "[$label] EC2 없음 → terraform apply (image=$tag)"
    (cd "$TERRAFORM_DIR" && terraform apply -auto-approve -var="app_image=$tag")
    ec2_ip="$(cd "$TERRAFORM_DIR" && terraform output -raw public_ip)"
  else
    log "[$label] 기존 EC2($ec2_ip) 이미지 교체 → $tag"
    local key="${SSH_KEY_PATH/#\~/$HOME}"
    ssh -i "$key" -o StrictHostKeyChecking=no -o ConnectTimeout=10 ec2-user@"$ec2_ip" \
      "cd /opt/load-test && sudo sed -i 's|APP_IMAGE=.*|APP_IMAGE=$tag|' .env && sudo docker compose pull app && sudo docker compose up -d app"
  fi

  local app_url
  app_url="http://${ec2_ip}:8080"
  log "[$label] EC2 URL: $app_url"
  log "[$label] Grafana:  $(cd "$TERRAFORM_DIR" && terraform output -raw grafana_url 2>/dev/null || echo 'N/A')"

  # ── 헬스체크 대기 ────────────────────────────────────────────────────
  local ssh_cmd
  ssh_cmd="$(cd "$TERRAFORM_DIR" && terraform output -raw ssh_command 2>/dev/null || echo '')"
  log "[$label] 헬스체크 대기 중 (최대 15분)..."
  [[ -n "$ssh_cmd" ]] && log "[$label] SSH 접속: $ssh_cmd"
  log "[$label] 앱 로그 확인: ssh 접속 후 → sudo cat /var/log/user_data.log"
  wait_healthy "$app_url" || {
    err "[$label] 헬스체크 실패 — destroy 후 종료합니다"
    (cd "$TERRAFORM_DIR" && terraform destroy -var="app_image=$tag")
    return 1
  }

  # ── k6 실행 ──────────────────────────────────────────────────────────
  log "[$label] k6 시작 (시나리오=$K6_SCENARIO, VUS=$K6_VUS, duration=$K6_DURATION)"
  local scenario_file="$K6_DIR/scenarios/${K6_SCENARIO}.js"

  if [[ ! -f "$scenario_file" ]]; then
    err "시나리오 파일 없음: $scenario_file"
    (cd "$TERRAFORM_DIR" && terraform destroy -auto-approve -var="app_image=$tag")
    return 1
  fi

  local k6_args=(
    run
    -e "VUS=$K6_VUS"
    -e "BASE_URL=$app_url"
    -e "DURATION=$K6_DURATION"
    --summary-export="$summary_file"
  )

  [[ -n "$TEST_JWT_TOKEN" ]] && k6_args+=(-e "TEST_JWT_TOKEN=$TEST_JWT_TOKEN")
  [[ -n "$LOAD_TEST_KEY"  ]] && k6_args+=(-e "LOAD_TEST_KEY=$LOAD_TEST_KEY")
  [[ "$KEEP_RAW" == "1"  ]] && k6_args+=(--out "json=$raw_file")
  k6_args+=(--out "web-dashboard=open=false")
  k6_args+=(--out experimental-prometheus-rw)

  local ec2_ip
  ec2_ip="$(cd "$TERRAFORM_DIR" && terraform output -raw public_ip)"

  (
    cd "$K6_DIR"
    K6_PROMETHEUS_RW_SERVER_URL="http://${ec2_ip}:9090/api/v1/write" \
    K6_PROMETHEUS_RW_NATIVE_HISTOGRAM_ENABLED="true" \
    K6_PROMETHEUS_RW_PUSH_INTERVAL="5s" \
    K6_PROMETHEUS_RW_STALE_MARKERS="true" \
    k6 "${k6_args[@]}" "$scenario_file"
  ) || err "[$label] k6 실행 중 오류 (결과가 불완전할 수 있음)"

  log "[$label] 결과 저장 → $summary_file"

  # ── terraform destroy (수동 확인) ────────────────────────────────────
  log "[$label] k6 완료. 대시보드 확인 후 destroy 진행하세요."
  log "[$label] terraform destroy (yes 입력 시 실행)"
  (
    cd "$TERRAFORM_DIR"
    terraform destroy \
      -var="app_image=$tag"
  )
  log "[$label] 완료 ✓"
}

do_test() {
  step "Phase 2: 배포 → 테스트 → Destroy (전체)"
  mkdir -p "$RESULTS_DIR"

  for pair in "${PAIRS[@]}"; do
    parse_pair "$pair"
    run_one_test "$PAIR_LABEL" "$PAIR_DESC" || true
  done
}

do_test_one() {
  local target="$1"
  local pair
  pair=$(find_pair "$target") || { err "알 수 없는 레이블: $target (유효값: before-tx after-tx before-sema after-sema before-async after-async)"; exit 1; }
  parse_pair "$pair"

  step "Phase 2: 배포 → 테스트 → Destroy [$PAIR_LABEL]"
  mkdir -p "$RESULTS_DIR"
  run_one_test "$PAIR_LABEL" "$PAIR_DESC"
}

wait_healthy() {
  local base_url="$1"
  local url="$base_url/actuator/health"
  local max=180  # 15분 (5초 × 180)
  for i in $(seq 1 $max); do
    if curl -sf --max-time 5 "$url" >/dev/null 2>&1; then
      log "  헬스체크 통과 (${i}번째 시도, $((i*5))초)"
      return 0
    fi
    echo -n "."
    sleep 5
  done
  echo ""
  return 1
}

# ═══════════════════════════════════════════════════════════════════════════
# Phase 3: 결과 비교 리포트 출력
# ═══════════════════════════════════════════════════════════════════════════
do_report() {
  step "Phase 3: 결과 비교 리포트"

  local report_file="$RESULTS_DIR/report.txt"

  {
    echo ""
    echo "════════════════════════════════════════════════════════════"
    echo "          부하테스트 아키텍처 비교 결과"
    echo "  시나리오: $K6_SCENARIO | VUS: $K6_VUS | duration: $K6_DURATION"
    echo "════════════════════════════════════════════════════════════"

    local comparisons=(
      "before-tx:after-tx:AI 트랜잭션 분리"
      "before-sema:after-sema:세마포어 도입"
      "before-async:after-async:비동기 전환"
    )

    local _ritype
    _ritype=$(grep -E 'default\s*=\s*"t[0-9]' "$TERRAFORM_DIR/variables.tf" | grep -o '"t[^"]*"' | tr -d '"' | head -1)
    local _rinstance_suffix=""
    case "$_ritype" in
      t3.small|t4g.small) _rinstance_suffix="" ;;
      *) _rinstance_suffix="-$(echo "$_ritype" | cut -d. -f2)" ;;
    esac

    for comp in "${comparisons[@]}"; do
      IFS=: read -r before after title <<< "$comp"
      local bf_file="$RESULTS_DIR/${before}-vus${K6_VUS}${_rinstance_suffix}-summary.json"
      local af_file="$RESULTS_DIR/${after}-vus${K6_VUS}${_rinstance_suffix}-summary.json"
      [[ ! -f "$bf_file" ]] && bf_file="$RESULTS_DIR/${before}-vus${K6_VUS}-summary.json"
      [[ ! -f "$af_file" ]] && af_file="$RESULTS_DIR/${after}-vus${K6_VUS}-summary.json"
      [[ ! -f "$bf_file" ]] && bf_file="$RESULTS_DIR/${before}-summary.json"
      [[ ! -f "$af_file" ]] && af_file="$RESULTS_DIR/${after}-summary.json"

      echo ""
      echo "── $title ─────────────────────────────────────────────"

      if [[ ! -f "$bf_file" || ! -f "$af_file" ]]; then
        echo "  결과 파일 없음 (테스트가 완료되지 않았을 수 있음)"
        echo "    before: $bf_file"
        echo "    after:  $af_file"
        continue
      fi

      printf "  %-22s %12s %12s %10s\n" "지표" "이전(before)" "이후(after)" "개선"
      printf "  %-22s %12s %12s %10s\n" "──────────────────────" "──────────" "──────────" "──────────"

      print_metric "$bf_file" "$af_file" '.metrics.http_req_duration["p(95)"]' "p95 응답시간 (ms)" "ms" "lower"
      print_metric "$bf_file" "$af_file" '.metrics.http_req_duration.avg'       "평균 응답시간 (ms)" "ms" "lower"
      print_metric "$bf_file" "$af_file" '.metrics.http_req_failed.rate'         "에러율"            "%"  "lower" "100"
      print_metric "$bf_file" "$af_file" '.metrics.http_reqs.rate'               "처리량 (req/s)"    ""   "higher"
    done

    echo ""
    echo "════════════════════════════════════════════════════════════"
    echo "결과 파일 위치: $RESULTS_DIR"
    echo "════════════════════════════════════════════════════════════"
    echo ""
  } | tee "$report_file"

  log "리포트 저장 → $report_file"
}

# jq로 값 추출 후 before/after/개선율 출력
# $5: "lower" = 낮을수록 좋음, "higher" = 높을수록 좋음
# $6: 배율 (소수를 %로 바꿀 때 100 지정)
print_metric() {
  local bf_file="$1" af_file="$2" jq_path="$3" label="$4" unit="$5"
  local direction="${6:-lower}" multiplier="${7:-1}"

  local bf af
  bf=$(jq -r "$jq_path // \"N/A\"" "$bf_file")
  af=$(jq -r "$jq_path // \"N/A\"" "$af_file")

  local bf_display af_display diff_display
  if [[ "$bf" == "N/A" || "$af" == "N/A" ]]; then
    bf_display="N/A" af_display="N/A" diff_display="N/A"
  else
    bf_display=$(awk -v v="$bf" -v m="$multiplier" 'BEGIN{printf "%.2f", v*m}')
    af_display=$(awk -v v="$af" -v m="$multiplier" 'BEGIN{printf "%.2f", v*m}')

    if [[ "$direction" == "lower" ]]; then
      diff_display=$(awk -v b="$bf" -v a="$af" 'BEGIN{
        if(b==0){print "N/A"}
        else{printf "%+.1f%%", (b-a)/b*100}
      }')
    else
      diff_display=$(awk -v b="$bf" -v a="$af" 'BEGIN{
        if(b==0){print "N/A"}
        else{printf "%+.1f%%", (a-b)/b*100}
      }')
    fi
  fi

  printf "  %-22s %11s%s %11s%s %10s\n" \
    "$label" "$bf_display" "$unit" "$af_display" "$unit" "$diff_display"
}

# ═══════════════════════════════════════════════════════════════════════════
# 메인
# ═══════════════════════════════════════════════════════════════════════════
LABELS="before-tx | after-tx | before-sema | after-sema | before-async | after-async | final-sema-2 | final-sema-20"

usage() {
  cat <<EOF
사용법: $(basename "$0") [PHASE] [LABEL]

PHASE:
  build           Docker 이미지 빌드 & GHCR push (6개 이미지 전체)
  build-one LABEL 특정 레이블 이미지만 빌드 & push
  test            EC2 배포 → k6 실행 → destroy 반복 (6회 전체)
  test-one  LABEL 특정 레이블 하나만 배포 → 테스트 → destroy
  report          저장된 결과로 비교 표 출력
  all             build → test → report 전체 실행 (기본)

LABEL:
  $LABELS

예시:
  # 전체 실행
  K6_VUS=50 ./bench-compare.sh all

  # 이미지 빌드만 (전체)
  ./bench-compare.sh build

  # 특정 이미지 하나만 빌드
  ./bench-compare.sh build-one before-tx

  # 이미 빌드된 이미지로 전체 테스트
  K6_SCENARIO=constant-vus K6_VUS=30 ./bench-compare.sh test

  # 특정 레이블 하나만 테스트
  K6_VUS=30 ./bench-compare.sh test-one after-async

  # 저장된 결과만 다시 출력
  ./bench-compare.sh report
EOF
}

main() {
  local phase="${1:-all}"

  case "$phase" in
    build)
      require_cmd docker git
      do_build
      ;;
    build-one)
      [[ -z "${2:-}" ]] && { err "build-one: LABEL 필요 ($LABELS)"; exit 1; }
      require_cmd docker git
      do_build_one "$2"
      ;;
    test)
      require_cmd terraform k6 jq curl
      do_test
      ;;
    test-one)
      [[ -z "${2:-}" ]] && { err "test-one: LABEL 필요 ($LABELS)"; exit 1; }
      require_cmd terraform k6 curl
      do_test_one "$2"
      ;;
    report)
      require_cmd jq
      do_report
      ;;
    all)
      require_cmd docker git terraform k6 jq curl
      do_build
      do_test
      do_report
      ;;
    -h|--help|help)
      usage
      ;;
    *)
      err "알 수 없는 phase: $phase"
      usage
      exit 1
      ;;
  esac
}

main "$@"
