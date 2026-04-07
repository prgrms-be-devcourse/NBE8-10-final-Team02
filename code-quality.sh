#!/usr/bin/env bash
# test-coverage.sh — 테스트 커버리지 검사 스크립트 (push 전 실행 권장)
# 사용법 예시 "C:\Program Files\Git\bin\bash.exe" code-quality.sh [--skip-tests]
# 혹은 그냥 ./code-quality.sh [--skip-tests]
# 옵션:
#   --skip-tests    테스트 실행 없이 기존 JaCoCo 리포트만 파싱
#   --help, -h      도움말 출력

set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/backend" && pwd)"
GRADLE="$BACKEND_DIR/gradlew"
JACOCO_XML="$BACKEND_DIR/build/reports/jacoco/test/jacocoTestReport.xml"
JACOCO_HTML="$BACKEND_DIR/build/reports/jacoco/test/html/index.html"

# ── 옵션 파싱 ────────────────────────────────────────────────────────────────
SKIP_TESTS=false

for arg in "$@"; do
  case $arg in
    --skip-tests) SKIP_TESTS=true ;;
    --help | -h)
      echo "사용법: $0 [--skip-tests]"
      echo ""
      echo "  --skip-tests    테스트를 실행하지 않고 기존 JaCoCo 리포트만 출력합니다."
      exit 0
      ;;
    *) echo "알 수 없는 옵션: $arg" >&2; exit 1 ;;
  esac
done

# ── 변경된 Java 파일 목록 (git) ───────────────────────────────────────────────
# 현재 브랜치에서 origin/dev 대비 변경된 파일, 없으면 uncommitted 변경분
get_changed_java_files() {
  local base
  base=$(git merge-base HEAD origin/dev 2>/dev/null \
      || git merge-base HEAD origin/main 2>/dev/null \
      || echo "")

  if [[ -n "$base" ]]; then
    git diff --name-only "$base" HEAD 2>/dev/null | grep '\.java$' || true
  else
    git diff --name-only HEAD 2>/dev/null | grep '\.java$' || true
  fi
}

# ── 커버리지 파싱 (Python3) ───────────────────────────────────────────────────
parse_coverage() {
  local xml="$1"
  local changed_files="$2"   # 개행 구분 파일명 목록 (빈 문자열 가능)

  local PYTHON
  PYTHON=$(command -v python3 2>/dev/null || command -v python 2>/dev/null || echo "")
  if [[ -z "$PYTHON" ]]; then
    echo "  ⚠️  python3/python 을 찾을 수 없어 커버리지 파싱을 건너뜁니다." >&2
    return 0
  fi

  "$PYTHON" - "$xml" "$changed_files" <<'PYEOF'
import sys, xml.etree.ElementTree as ET, re

# ── 색상 코드 ─────────────────────────────────────────────────────────────
GREEN  = "\033[32m"
YELLOW = "\033[33m"
RED    = "\033[31m"
CYAN   = "\033[36m"
BOLD   = "\033[1m"
DIM    = "\033[2m"
RESET  = "\033[0m"

def pct_color(p):
    if p >= 70: return GREEN
    if p >= 50: return YELLOW
    return RED

def bar(pct, width=10):
    filled = int(pct / 100 * width)
    return "█" * filled + "░" * (width - filled)

def fmt_pct(pct):
    c = pct_color(pct)
    return f"{c}{pct:5.1f}%{RESET}"

def fmt_badge(pct):
    if pct >= 70: return f"{GREEN}✅  PASS{RESET}"
    if pct >= 50: return f"{YELLOW}⚠️   LOW{RESET}"
    return f"{RED}❌  FAIL{RESET}"

# ── XML 파싱 ──────────────────────────────────────────────────────────────
xml_path      = sys.argv[1]
changed_input = sys.argv[2]   # 개행으로 구분된 변경 파일 경로들

tree = ET.parse(xml_path)
root = tree.getroot()

# 변경 파일명 집합 (경로 마지막 파일명만)
changed_basenames = set()
for path in changed_input.splitlines():
    path = path.strip()
    if path:
        changed_basenames.add(path.split("/")[-1].split("\\")[-1])

# ── 1. 전체 요약 ──────────────────────────────────────────────────────────
TYPES = [
    ("INSTRUCTION", "명령 (Instruction)"),
    ("LINE",        "라인 (Line)      "),
    ("BRANCH",      "브랜치 (Branch)  "),
    ("METHOD",      "메서드 (Method)  "),
]

overall = {}
for counter in root.findall("counter"):
    t = counter.get("type")
    missed  = int(counter.get("missed",  0))
    covered = int(counter.get("covered", 0))
    overall[t] = (covered, missed + covered)

print(f"\n{BOLD}  ┌─ 전체 커버리지 {'─'*40}┐{RESET}")
print(f"  │ {'유형':<22} {'커버':>7}  {'전체':>7}  {'비율':>6}  {'':10} │")
print(f"  │ {'─'*22} {'─'*7}  {'─'*7}  {'─'*6}  {'─'*10} │")
for key, label in TYPES:
    if key not in overall:
        continue
    cov, tot = overall[key]
    pct = cov / tot * 100 if tot > 0 else 0.0
    b   = bar(pct)
    print(f"  │ {label}  {cov:>7,}  {tot:>7,}  {fmt_pct(pct)}  {b} │")

instr_pct = overall["INSTRUCTION"][0] / overall["INSTRUCTION"][1] * 100 \
            if "INSTRUCTION" in overall and overall["INSTRUCTION"][1] > 0 else 0.0
print(f"  └{'─'*58}┘")
print(f"\n  전체 명령 커버리지: {BOLD}{instr_pct:.1f}%{RESET}  →  {fmt_badge(instr_pct)}")
print(f"  {DIM}기준: ≥ 70% PASS  |  ≥ 50% LOW  |  < 50% FAIL{RESET}")

# ── 2. 도메인별 커버리지 ──────────────────────────────────────────────────
# com/back/backend/domain/XXX/** 패키지를 도메인명으로 그룹핑
domain_stats = {}   # domain_name → {type: (covered, total)}
global_stats = {}

for pkg in root.findall("package"):
    name = pkg.get("name", "")
    # domain 패키지
    m = re.match(r"com/back/backend/domain/([^/]+)", name)
    if m:
        domain = m.group(1)
        bucket = domain_stats.setdefault(domain, {})
    elif name.startswith("com/back/backend/"):
        bucket = global_stats
    else:
        continue

    for counter in pkg.findall("counter"):
        t       = counter.get("type")
        missed  = int(counter.get("missed",  0))
        covered = int(counter.get("covered", 0))
        if t not in bucket:
            bucket[t] = [0, 0]
        bucket[t][0] += covered
        bucket[t][1] += covered + missed

if domain_stats:
    print(f"\n{BOLD}  ┌─ 도메인별 커버리지 (명령 기준) {'─'*26}┐{RESET}")
    print(f"  │ {'도메인':<18} {'커버':>7}  {'전체':>7}  {'비율':>6}  {'':10} │")
    print(f"  │ {'─'*18} {'─'*7}  {'─'*7}  {'─'*6}  {'─'*10} │")

    for domain in sorted(domain_stats):
        stats = domain_stats[domain]
        if "INSTRUCTION" not in stats:
            continue
        cov, tot = stats["INSTRUCTION"]
        pct = cov / tot * 100 if tot > 0 else 0.0
        b   = bar(pct)
        label = domain[:18]
        print(f"  │ {label:<18}  {cov:>7,}  {tot:>7,}  {fmt_pct(pct)}  {b} │")

    # global 패키지 (domain 외)
    if "INSTRUCTION" in global_stats:
        cov, tot = global_stats["INSTRUCTION"]
        pct = cov / tot * 100 if tot > 0 else 0.0
        b   = bar(pct)
        print(f"  │ {'global':<18}  {cov:>7,}  {tot:>7,}  {fmt_pct(pct)}  {b} │")

    print(f"  └{'─'*58}┘")

# ── 3. 변경 파일 커버리지 ─────────────────────────────────────────────────
if changed_basenames:
    changed_rows = []
    for pkg in root.findall("package"):
        for sf in pkg.findall("sourcefile"):
            fname = sf.get("name", "")
            if fname not in changed_basenames:
                continue
            pkg_name = pkg.get("name", "").replace("/", ".")
            counters = {c.get("type"): (int(c.get("covered", 0)),
                                         int(c.get("missed", 0)) + int(c.get("covered", 0)))
                        for c in sf.findall("counter")}
            changed_rows.append((fname, pkg_name, counters))

    if changed_rows:
        print(f"\n{BOLD}  ┌─ 변경 파일 커버리지 {'─'*37}┐{RESET}")
        print(f"  │ {'파일':<30} {'라인':>6}  {'브랜치':>7}  {'명령':>6} │")
        print(f"  │ {'─'*30} {'─'*6}  {'─'*7}  {'─'*6} │")
        for fname, pkg_name, c in sorted(changed_rows, key=lambda r: r[0]):
            def p(key):
                if key not in c or c[key][1] == 0: return "  N/A"
                cov, tot = c[key]
                pct = cov / tot * 100
                col = pct_color(pct)
                return f"{col}{pct:5.1f}%{RESET}"
            name_trunc = fname[:30]
            print(f"  │ {name_trunc:<30} {p('LINE')}  {p('BRANCH'):>14}  {p('INSTRUCTION'):>13} │")
        print(f"  └{'─'*58}┘")
    else:
        print(f"\n  {DIM}변경 파일 중 JaCoCo 리포트에 포함된 파일 없음{RESET}")
else:
    print(f"\n  {DIM}변경 파일 없음 (origin/dev 대비){RESET}")
PYEOF
}

# ── 헤더 ─────────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  📋 Code Quality 검사"
echo "========================================"

# ── 테스트 + JaCoCo ──────────────────────────────────────────────────────────
echo ""
echo "  🧪 테스트 + JaCoCo 커버리지"
echo ""

TEST_PASS=true

if [[ "$SKIP_TESTS" == "true" ]]; then
  echo "  ⚡ --skip-tests: 기존 리포트를 사용합니다."
  if [[ ! -f "$JACOCO_XML" ]]; then
    echo "  ❌ 리포트 없음. 먼저 ./gradlew test jacocoTestReport 를 실행하세요." >&2
    exit 1
  fi
else
  echo "  🔍 테스트 실행 중... (TestContainers 사용 시 Docker 필요)"
  if (cd "$BACKEND_DIR" && "$GRADLE" test jacocoTestReport --no-daemon -q 2>&1); then
    echo "  ✅  테스트 통과"
  else
    TEST_PASS=false
    echo "  ❌  테스트 실패"
    echo "  📄 상세 리포트: $BACKEND_DIR/build/reports/tests/test/index.html"
  fi
fi

# ── 변경 파일 수집 ────────────────────────────────────────────────────────────
CHANGED_FILES=$(get_changed_java_files)

# ── 커버리지 출력 ─────────────────────────────────────────────────────────────
parse_coverage "$JACOCO_XML" "$CHANGED_FILES"

echo ""
echo "  📄 HTML 리포트: $JACOCO_HTML"

# ── 최종 결과 ─────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
if [[ "$TEST_PASS" == "true" ]]; then
  echo "  ✅  모든 검사 통과"
else
  echo "  ❌  테스트 실패 — 수정 후 다시 실행하세요."
fi
echo "========================================"
echo ""

[[ "$TEST_PASS" == "true" ]]
