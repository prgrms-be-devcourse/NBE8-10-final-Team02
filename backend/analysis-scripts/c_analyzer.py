#!/usr/bin/env python3
# Install: pip install tree-sitter tree-sitter-c tree-sitter-cpp
"""
C/C++ static analyzer using tree-sitter with tree-sitter-c (for .c/.h files)
and tree-sitter-cpp (for .cpp/.cc/.cxx/.hpp files).

Output: JSON array of AnalysisNode to stdout.
Schema:
  [{"fqn":"src/network/socket::SocketManager","file_path":"src/...","loc_start":1,"loc_end":50,
    "node_type":"class","calls":["stdio.h","./utils.h"],"methods":[
      {"name":"connect","signature":"int connect(const char* host, int port)","loc_start":10,"loc_end":20}
    ]}]

FQN format:
  file path (no extension, slashes as-is) + "::" + type/function name
  e.g. src/network/socket.c + SocketManager -> src/network/socket::SocketManager
       src/network/socket.c + connect       -> src/network/socket::connect

Usage:
  python3 c_analyzer.py <repo_root> [--files f1 f2 ...] [--files-from /tmp/list.txt]
"""
import sys
import os
import json
import argparse

EXCLUDED_DIRS = {
    "__pycache__", ".venv", "venv", ".tox",
    "node_modules", ".git", "dist", "build", "target",
    ".ccache", ".cache",
}

C_EXTENSIONS   = {".c", ".h"}
CPP_EXTENSIONS = {".cpp", ".cc", ".cxx", ".hpp", ".hxx", ".h++"}
ALL_EXTENSIONS = C_EXTENSIONS | CPP_EXTENSIONS

# ---------------------------------------------------------------------------
# tree-sitter bootstrap — C
# ---------------------------------------------------------------------------
try:
    from tree_sitter import Language, Parser
    import tree_sitter_c as tsc
    C_LANGUAGE = Language(tsc.language())
    _c_parser = Parser(C_LANGUAGE)
    C_AVAILABLE = True
except Exception as _err_c:
    print(f"[c_analyzer] tree-sitter-c not available: {_err_c}", file=sys.stderr)
    C_AVAILABLE = False

# ---------------------------------------------------------------------------
# tree-sitter bootstrap — C++
# ---------------------------------------------------------------------------
try:
    from tree_sitter import Language, Parser  # noqa: F811 (already imported)
    import tree_sitter_cpp as tscpp
    CPP_LANGUAGE = Language(tscpp.language())
    _cpp_parser = Parser(CPP_LANGUAGE)
    CPP_AVAILABLE = True
except Exception as _err_cpp:
    print(f"[c_analyzer] tree-sitter-cpp not available: {_err_cpp}", file=sys.stderr)
    CPP_AVAILABLE = False


# ---------------------------------------------------------------------------
# Node helpers
# ---------------------------------------------------------------------------

def node_text(node, source_bytes: bytes) -> str:
    return source_bytes[node.start_byte:node.end_byte].decode("utf-8", errors="replace")


def find_first_child_by_type(node, *type_names):
    for child in node.children:
        if child.type in type_names:
            return child
    return None


def find_all_recursive_by_type(node, type_name: str, results=None):
    if results is None:
        results = []
    if node.type == type_name:
        results.append(node)
    for child in node.children:
        find_all_recursive_by_type(child, type_name, results)
    return results


# ---------------------------------------------------------------------------
# FQN helpers
# ---------------------------------------------------------------------------

def file_prefix(rel_path: str) -> str:
    """Strip extension from relative path to form the FQN prefix."""
    # Use posix-style slashes
    p = rel_path.replace("\\", "/")
    # Strip known extensions
    for ext in (".cpp", ".cc", ".cxx", ".hpp", ".hxx", ".h++", ".c", ".h"):
        if p.endswith(ext):
            p = p[: -len(ext)]
            break
    return p


# ---------------------------------------------------------------------------
# Include directive extraction (used as calls)
# ---------------------------------------------------------------------------

def extract_includes(root, source_bytes: bytes) -> list[str]:
    """
    Find all #include directives and return the included path as a string,
    stripping surrounding <> or "".
    e.g. #include <stdio.h>  -> "stdio.h"
         #include "utils.h"  -> "utils.h"
    """
    includes: list[str] = []
    for node in find_all_recursive_by_type(root, "preproc_include"):
        # child types: '#include', system_lib_string or string_literal
        for child in node.children:
            if child.type in ("system_lib_string", "string_literal"):
                raw = node_text(child, source_bytes).strip()
                # strip < > or " "
                raw = raw.strip("<>\"'")
                if raw:
                    includes.append(raw)
    return includes


# ---------------------------------------------------------------------------
# Function definition extraction
# ---------------------------------------------------------------------------

def get_function_name(fn_node, source_bytes: bytes) -> str | None:
    """
    Extract function name from a function_definition node.
    Structure: type declarator compound_statement
    The declarator contains the function_declarator which holds the identifier.
    """
    # Walk to find function_declarator -> identifier
    for decl in find_all_recursive_by_type(fn_node, "function_declarator"):
        name_node = find_first_child_by_type(decl, "identifier", "field_identifier",
                                              "qualified_identifier", "destructor_name",
                                              "operator_name", "template_function")
        if name_node:
            return node_text(name_node, source_bytes)
    # Fallback: direct identifier child
    name_node = find_first_child_by_type(fn_node, "identifier")
    if name_node:
        return node_text(name_node, source_bytes)
    return None


def get_function_signature(fn_node, source_bytes: bytes) -> str:
    """
    Build a signature by combining the type + declarator portion (everything
    except the trailing compound_statement body).
    """
    parts: list[str] = []
    for child in fn_node.children:
        if child.type == "compound_statement":
            break
        parts.append(node_text(child, source_bytes).strip())
    sig = " ".join(p for p in parts if p)
    # Collapse multiple whitespace
    import re
    sig = re.sub(r"\s+", " ", sig).strip()
    return sig


# ---------------------------------------------------------------------------
# Struct / class extraction
# ---------------------------------------------------------------------------

def get_struct_or_class_name(node, source_bytes: bytes) -> str | None:
    """Extract the name from a struct_specifier or class_specifier node."""
    name_node = find_first_child_by_type(node, "type_identifier")
    if name_node:
        return node_text(name_node, source_bytes)
    return None


def extract_class_methods(class_node, source_bytes: bytes) -> list[dict]:
    """Extract method declarations/definitions from a class_specifier (C++ only)."""
    methods: list[dict] = []
    body = find_first_child_by_type(class_node, "field_declaration_list")
    if body is None:
        return methods

    for child in body.children:
        # Method declarations inside class
        if child.type in ("function_definition", "declaration"):
            # Check if this looks like a function
            if child.type == "function_definition":
                name = get_function_name(child, source_bytes)
                sig = get_function_signature(child, source_bytes)
            else:
                # declaration: check for function_declarator inside
                has_fn_decl = bool(find_all_recursive_by_type(child, "function_declarator"))
                if not has_fn_decl:
                    continue
                # Find identifier
                fn_decl = find_all_recursive_by_type(child, "function_declarator")
                if not fn_decl:
                    continue
                name_node = find_first_child_by_type(
                    fn_decl[0], "identifier", "field_identifier",
                    "qualified_identifier", "destructor_name", "operator_name"
                )
                name = node_text(name_node, source_bytes) if name_node else None
                # Signature: everything in the declaration
                raw = node_text(child, source_bytes).strip()
                import re
                sig = re.sub(r"\s+", " ", raw).strip().rstrip(";")

            if not name:
                continue
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)
            methods.append({
                "name": name,
                "signature": sig,
                "loc_start": loc_start,
                "loc_end": loc_end,
            })
    return methods


# ---------------------------------------------------------------------------
# Main analysis
# ---------------------------------------------------------------------------

def analyze_file(file_path: str, repo_root: str) -> list[dict]:
    rel_path = os.path.relpath(file_path, repo_root).replace(os.sep, "/")
    ext = os.path.splitext(file_path)[1].lower()
    is_cpp = ext in CPP_EXTENSIONS

    if is_cpp and not CPP_AVAILABLE:
        print(f"[c_analyzer] skip {rel_path}: tree-sitter-cpp unavailable", file=sys.stderr)
        return []
    if not is_cpp and not C_AVAILABLE:
        print(f"[c_analyzer] skip {rel_path}: tree-sitter-c unavailable", file=sys.stderr)
        return []

    try:
        with open(file_path, "rb") as f:
            source_bytes = f.read()
    except OSError as e:
        print(f"[c_analyzer] skip {rel_path}: {e}", file=sys.stderr)
        return []

    # Heuristic: skip binary files
    if b"\x00" in source_bytes[:8192]:
        print(f"[c_analyzer] skip binary {rel_path}", file=sys.stderr)
        return []

    try:
        parser_to_use = _cpp_parser if is_cpp else _c_parser
        tree = parser_to_use.parse(source_bytes)
    except Exception as e:
        print(f"[c_analyzer] parse error {rel_path}: {e}", file=sys.stderr)
        return []

    root = tree.root_node
    prefix = file_prefix(rel_path)
    includes = extract_includes(root, source_bytes)
    results: list[dict] = []

    # Seen type names to avoid duplicates from forward declarations
    seen_types: set[str] = set()

    for child in root.children:
        # ------------------------------------------------------------------
        # struct_specifier (C and C++)
        # ------------------------------------------------------------------
        if child.type == "struct_specifier":
            name = get_struct_or_class_name(child, source_bytes)
            if not name or name in seen_types:
                continue
            # Only emit if it has a body (not a forward declaration)
            body = find_first_child_by_type(child, "field_declaration_list")
            if body is None:
                continue
            seen_types.add(name)
            fqn = f"{prefix}::{name}"
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)
            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "class",
                "calls": includes,
                "methods": [],  # C structs have no methods
            })

        # ------------------------------------------------------------------
        # class_specifier (C++ only)
        # ------------------------------------------------------------------
        elif child.type == "class_specifier":
            name = get_struct_or_class_name(child, source_bytes)
            if not name or name in seen_types:
                continue
            body = find_first_child_by_type(child, "field_declaration_list")
            if body is None:
                continue
            seen_types.add(name)
            fqn = f"{prefix}::{name}"
            methods = extract_class_methods(child, source_bytes)
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)
            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "class",
                "calls": includes,
                "methods": methods,
            })

        # ------------------------------------------------------------------
        # function_definition at file scope
        # ------------------------------------------------------------------
        elif child.type == "function_definition":
            name = get_function_name(child, source_bytes)
            if not name:
                continue
            fqn = f"{prefix}::{name}"
            sig = get_function_signature(child, source_bytes)
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)
            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "function",
                "calls": includes,
                "methods": [],
            })

        # ------------------------------------------------------------------
        # type_definition: typedef struct { ... } TypeName;
        # ------------------------------------------------------------------
        elif child.type == "type_definition":
            # Find struct_specifier inside
            struct_node = find_first_child_by_type(child, "struct_specifier", "class_specifier")
            if struct_node is None:
                continue
            # The typedef name is the last type_identifier child of type_definition
            type_ids = [c for c in child.children if c.type == "type_identifier"]
            if not type_ids:
                continue
            name = node_text(type_ids[-1], source_bytes)
            if name in seen_types:
                continue
            body = find_first_child_by_type(struct_node, "field_declaration_list")
            if body is None:
                continue
            seen_types.add(name)
            fqn = f"{prefix}::{name}"
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)
            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "class",
                "calls": includes,
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
            if os.path.splitext(fname)[1].lower() in ALL_EXTENSIONS:
                target_files.append(os.path.join(root, fname))
    return target_files


def main() -> None:
    parser = argparse.ArgumentParser(
        description="C/C++ static analyzer — outputs AnalysisNode JSON array"
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

    if not C_AVAILABLE and not CPP_AVAILABLE:
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
            print(f"[c_analyzer] cannot read --files-from: {e}", file=sys.stderr)
            target_files = []
    else:
        target_files = collect_files(repo_root)

    results: list[dict] = []
    for file_path in target_files:
        if not os.path.isfile(file_path):
            print(f"[c_analyzer] not found: {file_path}", file=sys.stderr)
            continue
        results.extend(analyze_file(file_path, repo_root))

    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[c_analyzer] fatal: {e}", file=sys.stderr)
        print("[]")
        sys.exit(1)
