#!/usr/bin/env python3
# Install: pip install tree-sitter tree-sitter-kotlin
"""
Kotlin static analyzer using tree-sitter with tree-sitter-kotlin grammar.

Output: JSON array of AnalysisNode to stdout.
Schema:
  [{"fqn":"com.example.UserService","file_path":"src/...","loc_start":1,"loc_end":50,
    "node_type":"class","calls":["com.example.OtherClass"],"methods":[
      {"name":"getUser","signature":"fun getUser(id: Long)","loc_start":10,"loc_end":20}
    ]}]

Usage:
  python3 kotlin_analyzer.py <repo_root> [--files f1 f2 ...] [--files-from /tmp/list.txt]
"""
import sys
import os
import json
import argparse

EXCLUDED_DIRS = {
    "__pycache__", ".venv", "venv", ".tox",
    "node_modules", ".git", "dist", "build", "target",
    ".gradle", ".idea", "out",
}

# ---------------------------------------------------------------------------
# tree-sitter bootstrap
# ---------------------------------------------------------------------------
try:
    from tree_sitter import Language, Parser
    import tree_sitter_kotlin as tskotlin
    KT_LANGUAGE = Language(tskotlin.language())
    _parser = Parser(KT_LANGUAGE)
    TREE_SITTER_AVAILABLE = True
except Exception as _ts_err:
    print(f"[kotlin_analyzer] tree-sitter not available: {_ts_err}", file=sys.stderr)
    TREE_SITTER_AVAILABLE = False


# ---------------------------------------------------------------------------
# Node helper utilities
# ---------------------------------------------------------------------------

def node_text(node, source_bytes: bytes) -> str:
    return source_bytes[node.start_byte:node.end_byte].decode("utf-8", errors="replace")


def find_children_by_type(node, type_name: str):
    return [c for c in node.children if c.type == type_name]


def find_first_child_by_type(node, type_name: str):
    for c in node.children:
        if c.type == type_name:
            return c
    return None


def find_all_by_type(node, type_name: str, results=None):
    """Recursively collect all descendant nodes of a given type."""
    if results is None:
        results = []
    if node.type == type_name:
        results.append(node)
    for child in node.children:
        find_all_by_type(child, type_name, results)
    return results


# ---------------------------------------------------------------------------
# File-based FQN fallback (same style as Python analyzer)
# ---------------------------------------------------------------------------

def file_based_fqn(rel_path: str, name: str) -> str:
    base = rel_path.replace("/", ".").removesuffix(".kt")
    return f"{base}.{name}"


# ---------------------------------------------------------------------------
# Package declaration extraction
# ---------------------------------------------------------------------------

def extract_package(tree_root, source_bytes: bytes) -> str:
    """Return the package name declared in the file, or empty string."""
    for child in tree_root.children:
        if child.type == "package_header":
            # package_header -> 'package' identifier
            for c in child.children:
                if c.type in ("identifier", "qualified_name", "simple_identifier"):
                    text = node_text(c, source_bytes).strip()
                    if text and text != "package":
                        return text
            # fallback: grab raw text minus 'package' keyword
            raw = node_text(child, source_bytes).strip()
            if raw.startswith("package"):
                return raw[len("package"):].strip()
    return ""


# ---------------------------------------------------------------------------
# Import extraction for calls
# ---------------------------------------------------------------------------

def extract_imports(tree_root, source_bytes: bytes) -> list[str]:
    """Return list of fully-qualified import strings."""
    imports: list[str] = []
    for child in tree_root.children:
        if child.type == "import_list":
            for header in child.children:
                if header.type == "import_header":
                    raw = node_text(header, source_bytes).strip()
                    # raw = "import com.example.Foo" or "import com.example.*"
                    if raw.startswith("import"):
                        fqn = raw[len("import"):].strip().rstrip(";")
                        if fqn:
                            imports.append(fqn)
        # Some grammars place import_header directly under source_file
        elif child.type == "import_header":
            raw = node_text(child, source_bytes).strip()
            if raw.startswith("import"):
                fqn = raw[len("import"):].strip().rstrip(";")
                if fqn:
                    imports.append(fqn)
    return imports


# ---------------------------------------------------------------------------
# Method extraction
# ---------------------------------------------------------------------------

def extract_method_signature(fn_node, source_bytes: bytes) -> str:
    """Build a human-readable signature for a function_declaration node."""
    name_node = find_first_child_by_type(fn_node, "simple_identifier")
    name = node_text(name_node, source_bytes) if name_node else "<unknown>"

    params_node = find_first_child_by_type(fn_node, "function_value_parameters")
    params_text = ""
    if params_node:
        params_text = node_text(params_node, source_bytes)

    return_type_node = find_first_child_by_type(fn_node, "type_reference")
    return_type = ""
    if return_type_node:
        return_type = f": {node_text(return_type_node, source_bytes)}"

    return f"fun {name}{params_text}{return_type}"


def extract_methods(class_body, source_bytes: bytes) -> list[dict]:
    methods: list[dict] = []
    if class_body is None:
        return methods
    # class_body or enum_class_body contains function_declaration
    for child in find_all_by_type(class_body, "function_declaration"):
        # Only direct children (depth 1) to avoid nested class methods
        # We check that parent chain hits class_body directly
        name_node = find_first_child_by_type(child, "simple_identifier")
        name = node_text(name_node, source_bytes) if name_node else "<unknown>"
        signature = extract_method_signature(child, source_bytes)
        loc_start = child.start_point[0] + 1
        loc_end = max(loc_start, child.end_point[0] + 1)
        methods.append({
            "name": name,
            "signature": signature,
            "loc_start": loc_start,
            "loc_end": loc_end,
        })
    return methods


# ---------------------------------------------------------------------------
# Main analysis
# ---------------------------------------------------------------------------

def analyze_file(file_path: str, repo_root: str) -> list[dict]:
    rel_path = os.path.relpath(file_path, repo_root).replace(os.sep, "/")
    try:
        with open(file_path, "rb") as f:
            source_bytes = f.read()
    except OSError as e:
        print(f"[kotlin_analyzer] skip {rel_path}: {e}", file=sys.stderr)
        return []

    try:
        tree = _parser.parse(source_bytes)
    except Exception as e:
        print(f"[kotlin_analyzer] parse error {rel_path}: {e}", file=sys.stderr)
        return []

    root = tree.root_node
    package_name = extract_package(root, source_bytes)
    imports = extract_imports(root, source_bytes)
    results: list[dict] = []

    def make_fqn(name: str) -> str:
        if package_name:
            return f"{package_name}.{name}"
        return file_based_fqn(rel_path, name)

    # Node types to treat as "class"
    CLASS_TYPES = {
        "class_declaration",
        "object_declaration",
        "interface_declaration",
        "enum_class_declaration",
    }

    for child in root.children:
        if child.type in CLASS_TYPES:
            name_node = find_first_child_by_type(child, "simple_identifier")
            if name_node is None:
                continue
            name = node_text(name_node, source_bytes)
            fqn = make_fqn(name)

            # Find class body
            body = find_first_child_by_type(child, "class_body") \
                or find_first_child_by_type(child, "enum_class_body")
            methods = extract_methods(body, source_bytes)

            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)

            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "class",
                "calls": imports,
                "methods": methods,
            })

        elif child.type == "function_declaration":
            name_node = find_first_child_by_type(child, "simple_identifier")
            if name_node is None:
                continue
            name = node_text(name_node, source_bytes)
            fqn = make_fqn(name)
            signature = extract_method_signature(child, source_bytes)

            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)

            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "function",
                "calls": imports,
                "methods": [],
            })

    return results


def collect_files(repo_root: str) -> list[str]:
    target_files: list[str] = []
    for root, dirs, files in os.walk(repo_root):
        dirs[:] = [
            d for d in dirs
            if d not in EXCLUDED_DIRS and not d.startswith(".")
        ]
        for fname in files:
            if fname.endswith(".kt"):
                target_files.append(os.path.join(root, fname))
    return target_files


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Kotlin static analyzer — outputs AnalysisNode JSON array"
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

    if not TREE_SITTER_AVAILABLE:
        print("[]")
        sys.exit(0)

    repo_root = os.path.abspath(args.repo_root)

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
            print(f"[kotlin_analyzer] cannot read --files-from: {e}", file=sys.stderr)
            target_files = []
    else:
        target_files = collect_files(repo_root)

    results: list[dict] = []
    for file_path in target_files:
        if not os.path.isfile(file_path):
            print(f"[kotlin_analyzer] not found: {file_path}", file=sys.stderr)
            continue
        results.extend(analyze_file(file_path, repo_root))

    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[kotlin_analyzer] fatal: {e}", file=sys.stderr)
        print("[]")
        sys.exit(1)
