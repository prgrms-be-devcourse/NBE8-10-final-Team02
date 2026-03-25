#!/usr/bin/env python3
"""
C/C++ 소스 정적 분석기 — stub.
C (.c, .h) 와 C++ (.cpp, .cc, .cxx, .hpp) 를 모두 처리한다.

출력 형식:
  [{"fqn":"file.FunctionName","file_path":"src/...","loc_start":1,"loc_end":50,
    "node_type":"function","calls":["other_header"],"methods":[]}]

사용법:
  python3 c_analyzer.py {repoRoot} [--files file1 file2 ...]
"""
import json
import argparse


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("repo_root")
    parser.add_argument("--files", nargs="*", default=None)
    parser.parse_args()

    # TODO: C/C++ AST 분석 구현 후 교체 (libclang 또는 pycparser 권장)
    print(json.dumps([]))


if __name__ == "__main__":
    main()
