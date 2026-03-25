#!/usr/bin/env python3
"""
Python static analyzer using stdlib ast only (no external deps).

Output: JSON array of AnalysisNode to stdout.
Schema:
  [{"fqn":"pkg.ClassName","file_path":"src/...","loc_start":1,"loc_end":50,
    "node_type":"class","calls":["other.Module"],"methods":[
      {"name":"method","signature":"method(arg)","loc_start":10,"loc_end":20}
    ]}]

Usage:
  python3 python_analyzer.py <repo_root> [--files f1 f2 ...] [--files-from /tmp/list.txt]
"""
import ast
import sys
import os
import json
import argparse

EXCLUDED_DIRS = {
    "__pycache__", ".venv", "venv", ".tox",
    "node_modules", ".git", "dist", "build", "target",
}


def get_module_fqn(rel_path: str) -> str:
    """Convert a relative file path to a dotted module FQN (without .py)."""
    # rel_path uses forward slashes already
    return rel_path.replace("/", ".").removesuffix(".py")


def build_import_map(tree: ast.AST) -> dict[str, str]:
    """
    Walk top-level import statements and return a map of
    local_name -> fully_qualified_name.

    Examples:
      import os               -> {"os": "os"}
      import os.path          -> {"os": "os", "path": "os.path"}  (alias target)
      from os.path import join -> {"join": "os.path.join"}
      from . import sibling   -> {"sibling": ".sibling"}
    """
    import_map: dict[str, str] = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                local = alias.asname if alias.asname else alias.name.split(".")[0]
                import_map[local] = alias.name
        elif isinstance(node, ast.ImportFrom):
            module = node.module or ""
            level = node.level  # 0 = absolute, 1+ = relative
            prefix = "." * level + module
            for alias in node.names:
                local = alias.asname if alias.asname else alias.name
                full = f"{prefix}.{alias.name}" if prefix else alias.name
                import_map[local] = full
    return import_map


def collect_calls(node: ast.AST, import_map: dict[str, str]) -> list[str]:
    """
    Walk an AST node and collect called FQNs by resolving against import_map.
    Handles:
      - foo()                  -> resolves "foo" if in import_map
      - foo.bar()              -> resolves "foo" prefix
      - module.Class.method()  -> resolves "module" prefix
    """
    seen: set[str] = set()
    for child in ast.walk(node):
        if not isinstance(child, ast.Call):
            continue
        func = child.func
        # Simple name call: foo()
        if isinstance(func, ast.Name):
            name = func.id
            if name in import_map:
                seen.add(import_map[name])
        # Attribute call: foo.bar() or a.b.c()
        elif isinstance(func, ast.Attribute):
            # Walk up the attribute chain to find the root name
            parts: list[str] = []
            cur = func
            while isinstance(cur, ast.Attribute):
                parts.append(cur.attr)
                cur = cur.value
            if isinstance(cur, ast.Name):
                root = cur.id
                parts.reverse()
                if root in import_map:
                    base = import_map[root]
                    seen.add(f"{base}.{'.'.join(parts)}" if parts else base)
    return sorted(seen)


def get_function_signature(node: ast.FunctionDef | ast.AsyncFunctionDef) -> str:
    """Build a readable signature string for a function/method node."""
    args_obj = node.args
    parts: list[str] = []

    # positional-only args (Python 3.8+)
    for a in args_obj.posonlyargs:
        parts.append(a.arg)

    for a in args_obj.args:
        parts.append(a.arg)

    if args_obj.vararg:
        parts.append(f"*{args_obj.vararg.arg}")

    for a in args_obj.kwonlyargs:
        parts.append(a.arg)

    if args_obj.kwarg:
        parts.append(f"**{args_obj.kwarg.arg}")

    return f"{'async ' if isinstance(node, ast.AsyncFunctionDef) else ''}{node.name}({', '.join(parts)})"


def analyze_file(file_path: str, repo_root: str) -> list[dict]:
    rel_path = os.path.relpath(file_path, repo_root).replace(os.sep, "/")
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            source = f.read()
    except OSError as e:
        print(f"[python_analyzer] skip {rel_path}: {e}", file=sys.stderr)
        return []

    try:
        tree = ast.parse(source, filename=file_path)
    except SyntaxError as e:
        print(f"[python_analyzer] parse error {rel_path}: {e}", file=sys.stderr)
        return []

    module_fqn = get_module_fqn(rel_path)
    import_map = build_import_map(tree)
    results: list[dict] = []

    # Only top-level nodes (direct children of Module)
    for node in ast.iter_child_nodes(tree):
        if isinstance(node, (ast.ClassDef,)):
            fqn = f"{module_fqn}.{node.name}"
            methods: list[dict] = []
            for item in ast.iter_child_nodes(node):
                if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    loc_end = item.end_lineno if item.end_lineno else item.lineno
                    methods.append({
                        "name": item.name,
                        "signature": get_function_signature(item),
                        "loc_start": item.lineno,
                        "loc_end": max(item.lineno, loc_end),
                    })

            calls = collect_calls(node, import_map)
            loc_end = node.end_lineno if node.end_lineno else node.lineno

            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": node.lineno,
                "loc_end": max(node.lineno, loc_end),
                "node_type": "class",
                "calls": calls,
                "methods": methods,
            })

        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            fqn = f"{module_fqn}.{node.name}"
            calls = collect_calls(node, import_map)
            loc_end = node.end_lineno if node.end_lineno else node.lineno

            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": node.lineno,
                "loc_end": max(node.lineno, loc_end),
                "node_type": "function",
                "calls": calls,
                "methods": [],
            })

    return results


def collect_files(repo_root: str) -> list[str]:
    target_files: list[str] = []
    for root, dirs, files in os.walk(repo_root):
        # Prune excluded directories in-place
        dirs[:] = [
            d for d in dirs
            if d not in EXCLUDED_DIRS and not d.startswith(".")
        ]
        for fname in files:
            if fname.endswith(".py"):
                target_files.append(os.path.join(root, fname))
    return target_files


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Python static analyzer — outputs AnalysisNode JSON array"
    )
    parser.add_argument("repo_root", help="Root directory of the repository")
    parser.add_argument(
        "--files", nargs="+", default=None,
        metavar="FILE",
        help="Analyze only these relative paths",
    )
    parser.add_argument(
        "--files-from", dest="files_from", default=None,
        metavar="FILE",
        help="Read relative paths from this file (one per line)",
    )
    args = parser.parse_args()

    repo_root = os.path.abspath(args.repo_root)

    # Resolve target file list
    if args.files:
        target_files = [
            os.path.join(repo_root, f.replace("/", os.sep)) for f in args.files
        ]
    elif args.files_from:
        try:
            with open(args.files_from, "r", encoding="utf-8") as fh:
                lines = [l.strip() for l in fh if l.strip()]
            target_files = [
                os.path.join(repo_root, l.replace("/", os.sep)) for l in lines
            ]
        except OSError as e:
            print(f"[python_analyzer] cannot read --files-from: {e}", file=sys.stderr)
            target_files = []
    else:
        target_files = collect_files(repo_root)

    results: list[dict] = []
    for file_path in target_files:
        if not os.path.isfile(file_path):
            print(f"[python_analyzer] not found: {file_path}", file=sys.stderr)
            continue
        results.extend(analyze_file(file_path, repo_root))

    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[python_analyzer] fatal: {e}", file=sys.stderr)
        print("[]")
        sys.exit(1)
