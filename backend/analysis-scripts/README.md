# analysis-scripts

언어별 정적 분석기 스크립트 모음.

`StaticAnalysisService`가 레포지토리 분석 시 언어별로 아래 스크립트를 호출한다.
분석 결과는 공통 `AnalysisNode` 스키마(JSON)로 출력되며 CodeIndex에 저장된다.

---

## 언어별 분석기 목록

| 언어 | 분석기 | 위치 | 방식 |
|---|---|---|---|
| **Java** | JavaParser | `domain/github/analysis/JavaStaticAnalyzer.java` | JVM 내부 직접 호출 |
| **Kotlin** | tree-sitter-kotlin | `kotlin_analyzer.py` | 외부 스크립트 |
| **Python** | stdlib `ast` | `python_analyzer.py` | 외부 스크립트 |
| **TypeScript / JavaScript** | ts-morph | `ts_analyzer.js` | 외부 스크립트 |
| **Go** | tree-sitter-go | `go_analyzer.py` | 외부 스크립트 |
| **Rust** | tree-sitter-rust | `rust_analyzer.py` | 외부 스크립트 |
| **C / C++** | tree-sitter-c / tree-sitter-cpp | `c_analyzer.py` | 외부 스크립트 |

> **Java가 이 폴더에 없는 이유**
> JavaParser는 JVM 라이브러리이므로 Spring Boot 앱 내부에서 직접 호출한다.
> 외부 프로세스 기동 비용 없이 실행되며, `build.gradle.kts`에 의존성이 선언되어 있다.

---

## 출력 스키마

모든 스크립트는 아래 형식의 JSON 배열을 stdout으로 출력한다.

```json
[
  {
    "fqn": "com.example.MyClass",
    "file_path": "src/main/java/com/example/MyClass.java",
    "loc_start": 10,
    "loc_end": 80,
    "node_type": "class",
    "calls": ["com.example.OtherClass"],
    "methods": [
      { "name": "doWork", "signature": "void doWork(String input)", "loc_start": 20, "loc_end": 35 }
    ]
  }
]
```

오류 발생 시 `[]`를 출력하고 경고는 stderr에 기록한다.

---

## 스크립트 인자

```
<script> <repo_root> [--files f1 f2 ...] [--files-from /tmp/list.txt]
```

- `--files`: 분석 대상 파일 목록 (상대 경로, 인라인)
- `--files-from`: 파일 목록이 담긴 텍스트 파일 경로 (파일 수 많을 때 ARG_MAX 초과 방지)
- 인자 없음: `repo_root` 전체 순회

---

## 서버 설치

```bash
# Python 스크립트 의존성 (tree-sitter 기반)
pip install tree-sitter tree-sitter-kotlin tree-sitter-go tree-sitter-rust tree-sitter-c tree-sitter-cpp

# TypeScript 분석기
cd /opt/analysis-scripts && npm install ts-morph
```

Python (`python_analyzer.py`)은 stdlib `ast`만 사용하므로 별도 설치 불필요.

---

## 경로 설정

| 환경 | 경로 |
|---|---|
| 로컬 개발 | `backend/analysis-scripts/` (`application-dev.yml: scripts-base-path`) |
| OCI 프로덕션 | `/opt/analysis-scripts/` (Docker volume mount) |
