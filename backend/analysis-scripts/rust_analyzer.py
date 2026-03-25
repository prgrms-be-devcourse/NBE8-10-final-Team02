#!/usr/bin/env python3
# Install: pip install tree-sitter tree-sitter-rust
"""
Rust static analyzer using tree-sitter with tree-sitter-rust grammar.

Output: JSON array of AnalysisNode to stdout.
Schema:
  [{"fqn":"crate::services::auth::AuthHandler","file_path":"src/...","loc_start":1,"loc_end":50,
    "node_type":"class","calls":["crate::other"],"methods":[
      {"name":"handle","signature":"fn handle(&self, req: Request) -> Response","loc_start":10,"loc_end":20}
    ]}]

FQN format:
  src/services/auth.rs + AuthHandler  -> crate::services::auth::AuthHandler
  src/lib.rs + Config                 -> crate::Config
  src/main.rs + Config                -> crate::Config

Usage:
  python3 rust_analyzer.py <repo_root> [--files f1 f2 ...] [--files-from /tmp/list.txt]
"""
import sys
import os
import json
import argparse

EXCLUDED_DIRS = {
    "__pycache__", ".venv", "venv", ".tox",
    "node_modules", ".git", "dist", "build", "target",
    ".cargo", ".rustup",
}

# ---------------------------------------------------------------------------
# tree-sitter bootstrap
# ---------------------------------------------------------------------------
try:
    from tree_sitter import Language, Parser
    import tree_sitter_rust as tsrust
    RUST_LANGUAGE = Language(tsrust.language())
    _parser = Parser(RUST_LANGUAGE)
    TREE_SITTER_AVAILABLE = True
except Exception as _ts_err:
    print(f"[rust_analyzer] tree-sitter not available: {_ts_err}", file=sys.stderr)
    TREE_SITTER_AVAILABLE = False


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


def find_all_direct_by_type(node, *type_names):
    return [c for c in node.children if c.type in type_names]


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

def rel_path_to_crate_module(rel_path: str) -> str:
    """
    Convert a relative .rs path to a crate-relative module path string.

    src/lib.rs    -> ""          (crate root)
    src/main.rs   -> ""          (crate root)
    src/foo.rs    -> "foo"
    src/foo/mod.rs-> "foo"
    src/a/b/c.rs  -> "a::b::c"
    """
    # Normalise separators
    parts = rel_path.replace("\\", "/").split("/")

    # Strip leading "src" if present
    if parts and parts[0] == "src":
        parts = parts[1:]

    # Strip .rs extension from last part
    if parts:
        last = parts[-1]
        if last.endswith(".rs"):
            last = last[:-3]
            parts[-1] = last

    # lib / main are the crate root
    if len(parts) == 1 and parts[0] in ("lib", "main"):
        return ""
    # mod.rs represents the parent directory module
    if parts and parts[-1] == "mod":
        parts = parts[:-1]

    return "::".join(p for p in parts if p)


def make_fqn(module_path: str, name: str) -> str:
    if module_path:
        return f"crate::{module_path}::{name}"
    return f"crate::{name}"


# ---------------------------------------------------------------------------
# Import / use declaration extraction
# ---------------------------------------------------------------------------

def extract_use_declarations(root, source_bytes: bytes) -> list[str]:
    """
    Collect all use_declaration texts as call targets.
    e.g. `use std::collections::HashMap;` -> "std::collections::HashMap"
    """
    uses: list[str] = []
    for node in find_all_recursive_by_type(root, "use_declaration"):
        raw = node_text(node, source_bytes).strip()
        # strip leading 'use' and trailing ';'
        if raw.startswith("use"):
            path_str = raw[3:].strip().rstrip(";").strip()
            if path_str:
                uses.append(path_str)
    return uses


# ---------------------------------------------------------------------------
# Function/method extraction
# ---------------------------------------------------------------------------

def get_fn_name(fn_node, source_bytes: bytes) -> str:
    name_node = find_first_child_by_type(fn_node, "identifier")
    if name_node:
        return node_text(name_node, source_bytes)
    return "<unknown>"


def get_fn_signature(fn_node, source_bytes: bytes) -> str:
    """
    Build a signature string by grabbing:
      fn name + parameters + optional return type
    We reconstruct from child nodes rather than raw text to keep it clean.
    """
    name = get_fn_name(fn_node, source_bytes)
    params_node = find_first_child_by_type(fn_node, "parameters")
    params_text = node_text(params_node, source_bytes) if params_node else "()"
    ret_node = find_first_child_by_type(fn_node, "type_identifier", "scoped_type_identifier",
                                        "generic_type", "reference_type", "primitive_type",
                                        "abstract_return_type")
    # The return type in tree-sitter-rust is the node after '->' token
    # A simpler approach: look for the '->' token among children
    ret_str = ""
    found_arrow = False
    for child in fn_node.children:
        if child.type == "->":
            found_arrow = True
        elif found_arrow and child.type not in ("{", "block"):
            ret_str = f" -> {node_text(child, source_bytes)}"
            break
        elif found_arrow and child.type == "block":
            break

    return f"fn {name}{params_text}{ret_str}"


def extract_impl_methods(impl_node, source_bytes: bytes) -> list[dict]:
    """Extract methods from an impl_item node."""
    methods: list[dict] = []
    # declaration_list contains the items
    decl_list = find_first_child_by_type(impl_node, "declaration_list")
    if decl_list is None:
        return methods
    for child in decl_list.children:
        if child.type == "function_item":
            name = get_fn_name(child, source_bytes)
            signature = get_fn_signature(child, source_bytes)
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
        print(f"[rust_analyzer] skip {rel_path}: {e}", file=sys.stderr)
        return []

    try:
        tree = _parser.parse(source_bytes)
    except Exception as e:
        print(f"[rust_analyzer] parse error {rel_path}: {e}", file=sys.stderr)
        return []

    root = tree.root_node
    module_path = rel_path_to_crate_module(rel_path)
    use_decls = extract_use_declarations(root, source_bytes)
    results: list[dict] = []

    # Map of type name -> node entry (for merging impl methods later)
    type_nodes: dict[str, dict] = {}

    # -----------------------------------------------------------------------
    # Pass 1: collect struct / enum / trait declarations
    # -----------------------------------------------------------------------
    for child in root.children:
        if child.type in ("struct_item", "enum_item", "trait_item"):
            name_node = find_first_child_by_type(child, "type_identifier")
            if name_node is None:
                continue
            name = node_text(name_node, source_bytes)
            fqn = make_fqn(module_path, name)
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)

            entry = {
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "class",
                "calls": use_decls,
                "methods": [],
            }
            type_nodes[name] = entry
            results.append(entry)

        elif child.type == "function_item":
            name = get_fn_name(child, source_bytes)
            fqn = make_fqn(module_path, name)
            signature = get_fn_signature(child, source_bytes)
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)
            results.append({
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "function",
                "calls": use_decls,
                "methods": [],
            })

    # -----------------------------------------------------------------------
    # Pass 2: attach impl methods to their type
    # -----------------------------------------------------------------------
    for child in root.children:
        if child.type != "impl_item":
            continue

        # Find the type being implemented
        # impl_item: 'impl' [type_parameters] type_identifier [for ...] { ... }
        # or: impl TypeName { ... }
        impl_type_name = None
        for c in child.children:
            if c.type == "type_identifier":
                impl_type_name = node_text(c, source_bytes)
                break
            elif c.type == "generic_type":
                # generic_type -> type_identifier
                ti = find_first_child_by_type(c, "type_identifier")
                if ti:
                    impl_type_name = node_text(ti, source_bytes)
                break

        methods = extract_impl_methods(child, source_bytes)

        if impl_type_name and impl_type_name in type_nodes:
            # Merge into existing type entry
            type_nodes[impl_type_name]["methods"].extend(methods)
        elif impl_type_name:
            # impl for a type defined elsewhere (e.g. trait impl) — create entry
            fqn = make_fqn(module_path, impl_type_name)
            loc_start = child.start_point[0] + 1
            loc_end = max(loc_start, child.end_point[0] + 1)
            entry = {
                "fqn": fqn,
                "file_path": rel_path,
                "loc_start": loc_start,
                "loc_end": loc_end,
                "node_type": "class",
                "calls": use_decls,
                "methods": methods,
            }
            type_nodes[impl_type_name] = entry
            results.append(entry)

    return results


def collect_files(repo_root: str) -> list[str]:
    target_files: list[str] = []
    for root, dirs, files in os.walk(repo_root):
        dirs[:] = [
            d for d in dirs
            if d not in EXCLUDED_DIRS and not d.startswith(".")
        ]
        for fname in files:
            if fname.endswith(".rs"):
                target_files.append(os.path.join(root, fname))
    return target_files


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Rust static analyzer — outputs AnalysisNode JSON array"
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
            print(f"[rust_analyzer] cannot read --files-from: {e}", file=sys.stderr)
            target_files = []
    else:
        target_files = collect_files(repo_root)

    results: list[dict] = []
    for file_path in target_files:
        if not os.path.isfile(file_path):
            print(f"[rust_analyzer] not found: {file_path}", file=sys.stderr)
            continue
        results.extend(analyze_file(file_path, repo_root))

    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[rust_analyzer] fatal: {e}", file=sys.stderr)
        print("[]")
        sys.exit(1)
