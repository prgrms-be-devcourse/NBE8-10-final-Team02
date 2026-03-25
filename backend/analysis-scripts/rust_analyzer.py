#!/usr/bin/env python3
"""
Rust 소스 정적 분석기 — stub.

출력 형식:
  [{"fqn":"crate::module::TypeName","file_path":"src/...","loc_start":1,"loc_end":50,
    "node_type":"class","calls":["crate::other"],"methods":[
      {"name":"method","signature":"method(arg)","loc_start":10,"loc_end":20}
    ]}]

사용법:
  python3 rust_analyzer.py {repoRoot} [--files file1 file2 ...]
"""
import json
import argparse


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("repo_root")
    parser.add_argument("--files", nargs="*", default=None)
    parser.parse_args()

    # TODO: Rust AST 분석 구현 후 교체
    print(json.dumps([]))


if __name__ == "__main__":
    main()
