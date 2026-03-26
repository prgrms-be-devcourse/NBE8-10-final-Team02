#!/usr/bin/env python3
# Install: pip install tree-sitter tree-sitter-go
"""
Go static analyzer using tree-sitter.

Usage:
  python3 go_analyzer.py <repo_root> [--files f1 f2 ...]
  python3 go_analyzer.py <repo_root> --files-from /path/to/list.txt
  python3 go_analyzer.py <repo_root>   # walks whole repo

Output: JSON array to stdout. Warnings to stderr only.
"""

import sys
import json
import os
import argparse
from pathlib import Path

try:
    import tree_sitter_go as tsg
    from tree_sitter import Language, Parser
except ImportError:
    sys.stderr.write(
        "WARNING: tree-sitter or tree-sitter-go not installed. "
        "Run: pip install tree-sitter tree-sitter-go\n"
    )
    print("[]")
    sys.exit(0)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

EXCLUDED_DIRS = {
    ".git", ".idea", "vendor", "testdata",
    "node_modules", ".cache", "dist", "build", "target",
}

GO_LANGUAGE = Language(tsg.language())


# ---------------------------------------------------------------------------
# Tree-sitter helpers
# ---------------------------------------------------------------------------

def node_text(node, src: bytes) -> str:
    return src[node.start_byte:node.end_byte].decode("utf-8", errors="replace")


def find_children_by_type(node, *types):
    return [c for c in node.children if c.type in types]


def find_first_child_by_type(node, *types):
    for c in node.children:
        if c.type in types:
            return c
    return None


def walk_nodes(node):
    """Depth-first generator over all nodes."""
    yield node
    for child in node.children:
        yield from walk_nodes(child)


# ---------------------------------------------------------------------------
# Package / import parsing
# ---------------------------------------------------------------------------

def get_package_name(root, src: bytes) -> str:
    for node in root.children:
        if node.type == "package_clause":
            name_node = find_first_child_by_type(node, "package_identifier")
            if name_node:
                return node_text(name_node, src)
    return ""


def parse_imports(root, src: bytes) -> dict:
    """
    Return a mapping of alias -> import_path (without quotes).
    The alias defaults to the last segment of the import path when not explicit.
    """
    alias_map: dict[str, str] = {}

    for node in walk_nodes(root):
        if node.type == "import_declaration":
            spec_list = find_first_child_by_type(node, "import_spec_list")
            if spec_list:
                specs = find_children_by_type(spec_list, "import_spec")
            else:
                specs = find_children_by_type(node, "import_spec")

            for spec in specs:
                path_node = find_first_child_by_type(spec, "interpreted_string_literal", "raw_string_literal")
                if not path_node:
                    continue
                raw_path = node_text(path_node, src).strip('"').strip('`')

                alias_node = find_first_child_by_type(spec, "package_identifier", "blank_identifier", "dot")
                if alias_node and alias_node.type == "package_identifier":
                    alias = node_text(alias_node, src)
                else:
                    alias = raw_path.split("/")[-1]

                alias_map[alias] = raw_path

    return alias_map


# ---------------------------------------------------------------------------
# Call extraction
# ---------------------------------------------------------------------------

def extract_calls(node, src: bytes, import_map: dict) -> list[str]:
    """
    Find all call_expression nodes whose function is a selector_expression
    where the left side matches a known import alias.
    Returns list of "import_path.FuncName" strings (deduplicated, sorted).
    """
    calls: set[str] = set()

    for n in walk_nodes(node):
        if n.type != "call_expression":
            continue
        func_node = find_first_child_by_type(n, "selector_expression")
        if not func_node:
            continue

        # selector_expression: <operand> "." <field_identifier>
        operand = None
        field = None
        for child in func_node.children:
            if child.type in ("identifier", "package_identifier"):
                operand = node_text(child, src)
            elif child.type == "field_identifier":
                field = node_text(child, src)

        if operand and field and operand in import_map:
            import_path = import_map[operand]
            calls.add(f"{import_path}.{field}")

    return sorted(calls)


# ---------------------------------------------------------------------------
# Receiver type extraction
# ---------------------------------------------------------------------------

def extract_receiver_type(node, src: bytes) -> str:
    """
    Extract the base type name from a method receiver.
    Handles *T and T forms.
    """
    param_list = find_first_child_by_type(node, "parameter_list")
    if not param_list:
        return ""
    param_decl = find_first_child_by_type(param_list, "parameter_declaration")
    if not param_decl:
        return ""

    type_node = None
    for child in param_decl.children:
        if child.type not in ("identifier", ",", "(", ")"):
            type_node = child
            break

    if type_node is None:
        return ""

    if type_node.type == "pointer_type":
        inner = find_first_child_by_type(type_node, "type_identifier")
        if inner:
            return node_text(inner, src)
    elif type_node.type == "type_identifier":
        return node_text(type_node, src)

    return node_text(type_node, src).lstrip("*")


# ---------------------------------------------------------------------------
# Method signature from interface
# ---------------------------------------------------------------------------

def extract_interface_methods(struct_or_iface_node, src: bytes) -> list[dict]:
    """
    Extract method signatures from an interface_type node.
    Returns list of dicts with name, signature, loc_start, loc_end.
    """
    methods = []
    for node in walk_nodes(struct_or_iface_node):
        if node.type == "method_elem":
            name_node = find_first_child_by_type(node, "field_identifier")
            if not name_node:
                continue
            name = node_text(name_node, src)
            sig = node_text(node, src).replace("\n", " ").strip()
            methods.append({
                "name": name,
                "signature": sig,
                "loc_start": node.start_point[0] + 1,
                "loc_end": node.end_point[0] + 1,
            })
    return methods


# ---------------------------------------------------------------------------
# Function / method signature string
# ---------------------------------------------------------------------------

def build_func_signature(node, src: bytes) -> str:
    """Build a readable signature string from a function or method declaration node."""
    return node_text(node, src).split("{")[0].strip()


# ---------------------------------------------------------------------------
# Core analysis of a single file
# ---------------------------------------------------------------------------

def analyze_file(file_path: Path, repo_root: Path, parser: Parser) -> list[dict]:
    try:
        src = file_path.read_bytes()
    except OSError as e:
        sys.stderr.write(f"WARNING: Cannot read {file_path}: {e}\n")
        return []

    try:
        tree = parser.parse(src)
    except Exception as e:
        sys.stderr.write(f"WARNING: Parse error in {file_path}: {e}\n")
        return []

    root = tree.root_node
    pkg_name = get_package_name(root, src)
    import_map = parse_imports(root, src)

    try:
        rel_path = file_path.relative_to(repo_root)
        rel_str = str(rel_path).replace("\\", "/")
    except ValueError:
        rel_str = str(file_path).replace("\\", "/")

    results: list[dict] = []

    for node in root.children:
        # ----------------------------------------------------------------
        # type_declaration → struct or interface
        # ----------------------------------------------------------------
        if node.type == "type_declaration":
            for spec in find_children_by_type(node, "type_spec"):
                name_node = find_first_child_by_type(spec, "type_identifier")
                if not name_node:
                    continue
                type_name = node_text(name_node, src)
                fqn = f"{pkg_name}.{type_name}" if pkg_name else type_name

                body = None
                node_type_str = None
                methods = []

                for child in spec.children:
                    if child.type == "struct_type":
                        body = child
                        node_type_str = "class"
                        methods = []
                        break
                    elif child.type == "interface_type":
                        body = child
                        node_type_str = "class"
                        methods = extract_interface_methods(child, src)
                        break

                if node_type_str is None:
                    continue

                calls = extract_calls(spec, src, import_map)

                results.append({
                    "fqn": fqn,
                    "file_path": rel_str,
                    "loc_start": node.start_point[0] + 1,
                    "loc_end": node.end_point[0] + 1,
                    "node_type": node_type_str,
                    "calls": calls,
                    "methods": methods,
                })

        # ----------------------------------------------------------------
        # function_declaration (top-level function)
        # ----------------------------------------------------------------
        elif node.type == "function_declaration":
            name_node = find_first_child_by_type(node, "identifier")
            if not name_node:
                continue
            func_name = node_text(name_node, src)
            fqn = f"{pkg_name}.{func_name}" if pkg_name else func_name
            calls = extract_calls(node, src, import_map)

            results.append({
                "fqn": fqn,
                "file_path": rel_str,
                "loc_start": node.start_point[0] + 1,
                "loc_end": node.end_point[0] + 1,
                "node_type": "function",
                "calls": calls,
                "methods": [],
            })

        # ----------------------------------------------------------------
        # method_declaration (func with receiver)
        # ----------------------------------------------------------------
        elif node.type == "method_declaration":
            name_node = find_first_child_by_type(node, "field_identifier")
            if not name_node:
                continue
            method_name = node_text(name_node, src)
            receiver_type = extract_receiver_type(node, src)

            if receiver_type:
                fqn = f"{pkg_name}.{receiver_type}.{method_name}" if pkg_name else f"{receiver_type}.{method_name}"
            else:
                fqn = f"{pkg_name}.{method_name}" if pkg_name else method_name

            calls = extract_calls(node, src, import_map)

            results.append({
                "fqn": fqn,
                "file_path": rel_str,
                "loc_start": node.start_point[0] + 1,
                "loc_end": node.end_point[0] + 1,
                "node_type": "function",
                "calls": calls,
                "methods": [],
            })

    return results


# ---------------------------------------------------------------------------
# File discovery
# ---------------------------------------------------------------------------

def collect_go_files(repo_root: Path, skip_tests: bool = True) -> list[Path]:
    files: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(repo_root):
        # Prune excluded directories in-place so os.walk skips them
        dirnames[:] = [d for d in dirnames if d not in EXCLUDED_DIRS]
        for fname in filenames:
            if not fname.endswith(".go"):
                continue
            if skip_tests and fname.endswith("_test.go"):
                continue
            files.append(Path(dirpath) / fname)
    return files


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser_cli = argparse.ArgumentParser(
        description="Go static analyzer — outputs JSON array of symbol records."
    )
    parser_cli.add_argument(
        "repo_root",
        help="Root directory of the repository to analyze.",
    )
    group = parser_cli.add_mutually_exclusive_group()
    group.add_argument(
        "--files",
        nargs="+",
        metavar="FILE",
        help="Explicit list of .go files to analyze.",
    )
    group.add_argument(
        "--files-from",
        metavar="LIST_FILE",
        help="Path to a text file containing one .go file path per line.",
    )

    args = parser_cli.parse_args()
    repo_root = Path(args.repo_root).resolve()

    if not repo_root.is_dir():
        sys.stderr.write(f"ERROR: repo_root is not a directory: {repo_root}\n")
        print("[]")
        sys.exit(1)

    # Determine file list
    if args.files:
        go_files = [Path(f).resolve() for f in args.files]
        skip_tests = False
    elif args.files_from:
        list_path = Path(args.files_from)
        if not list_path.is_file():
            sys.stderr.write(f"ERROR: --files-from path not found: {list_path}\n")
            print("[]")
            sys.exit(1)
        lines = list_path.read_text(encoding="utf-8").splitlines()
        go_files = [Path(line.strip()).resolve() for line in lines if line.strip()]
        skip_tests = False
    else:
        go_files = collect_go_files(repo_root, skip_tests=True)
        skip_tests = True

    # Build tree-sitter parser
    ts_parser = Parser(GO_LANGUAGE)

    all_results: list[dict] = []
    for go_file in go_files:
        if not go_file.is_file():
            sys.stderr.write(f"WARNING: Skipping missing file: {go_file}\n")
            continue
        file_results = analyze_file(go_file, repo_root, ts_parser)
        all_results.extend(file_results)

    print(json.dumps(all_results, ensure_ascii=False, indent=None))


if __name__ == "__main__":
    main()
