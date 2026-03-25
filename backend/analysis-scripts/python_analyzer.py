#!/usr/bin/env python3
"""
Python 소스 정적 분석기.
ast (stdlib) + pyan3를 사용하여 클래스/함수 구조와 호출 관계를 추출한다.

출력: AnalysisNode JSON 배열 (stdout)
형식:
  [{"fqn":"module.ClassName","file_path":"src/...","loc_start":1,"loc_end":50,
    "node_type":"class","calls":["other.Module"],"methods":[...]}]

사용법:
  python3 python_analyzer.py {repoRoot} [--files file1 file2 ...]

설치 요구사항:
  pip install pyan3
"""
import ast
import sys
import os
import json
import argparse


def get_module_fqn(file_path: str, repo_root: str) -> str:
    rel = os.path.relpath(file_path, repo_root)
    module = rel.replace(os.sep, ".").removesuffix(".py")
    return module


def analyze_file(file_path: str, repo_root: str) -> list[dict]:
    rel_path = os.path.relpath(file_path, repo_root).replace(os.sep, "/")
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            source = f.read()
        tree = ast.parse(source, filename=file_path)
    except SyntaxError:
        return []

    module_fqn = get_module_fqn(file_path, repo_root)
    lines = source.splitlines()
    nodes = []

    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef):
            fqn = f"{module_fqn}.{node.name}"
            methods = []
            for item in node.body:
                if isinstance(item, ast.FunctionDef | ast.AsyncFunctionDef):
                    args = ", ".join(
                        a.arg for a in item.args.args if a.arg != "self"
                    )
                    methods.append({
                        "name": item.name,
                        "signature": f"{item.name}({args})",
                        "loc_start": item.lineno,
                        "loc_end": item.end_lineno or item.lineno,
                    })
            # 호출 관계: 클래스 내 Attribute 참조 수집 (근사치)
            calls = set()
            for child in ast.walk(node):
                if isinstance(child, ast.Attribute):
                    calls.add(child.attr)

            nodes.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": node.lineno,
                "loc_end": node.end_lineno or node.lineno,
                "node_type": "class",
                "calls": list(calls),
                "methods": methods,
            })

    return nodes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("repo_root")
    parser.add_argument("--files", nargs="*", default=None)
    args = parser.parse_args()

    repo_root = args.repo_root
    results = []

    if args.files:
        target_files = [os.path.join(repo_root, f) for f in args.files]
    else:
        target_files = []
        for root, dirs, files in os.walk(repo_root):
            # 숨김 디렉토리 제외
            dirs[:] = [d for d in dirs if not d.startswith(".")]
            for f in files:
                if f.endswith(".py"):
                    target_files.append(os.path.join(root, f))

    for file_path in target_files:
        if os.path.exists(file_path):
            results.extend(analyze_file(file_path, repo_root))

    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    main()
