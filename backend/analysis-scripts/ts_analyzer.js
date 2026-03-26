#!/usr/bin/env node
// Install: npm install ts-morph
// Node version: 16+
//
// TypeScript/JavaScript static analyzer using ts-morph.
//
// Output: JSON array of AnalysisNode to stdout.
// Schema:
//   [{"fqn":"src/services/AuthService.AuthService","file_path":"src/...","loc_start":1,"loc_end":50,
//     "node_type":"class","calls":["react","./utils"],"methods":[
//       {"name":"login","signature":"login(email: string, password: string)","loc_start":10,"loc_end":20}
//     ]}]
//
// Usage:
//   node ts_analyzer.js <repo_root> [--files f1 f2 ...] [--files-from /tmp/list.txt]

"use strict";

const path = require("path");
const fs = require("fs");

// Graceful degradation if ts-morph is not installed
let tsMorph;
try {
  tsMorph = require("ts-morph");
} catch (e) {
  process.stderr.write("[ts_analyzer] ts-morph not installed — outputting []\n");
  process.stdout.write("[]");
  process.exit(0);
}

const { Project, SyntaxKind } = tsMorph;

// ---------------------------------------------------------------------------
// Argument parsing
// ---------------------------------------------------------------------------
const argv = process.argv.slice(2);

if (!argv[0]) {
  process.stderr.write("[ts_analyzer] Usage: node ts_analyzer.js <repo_root> [--files ...]\n");
  process.stdout.write("[]");
  process.exit(1);
}

const repoRoot = path.resolve(argv[0]);

let explicitFiles = null; // null = walk entire repo

const filesIdx = argv.indexOf("--files");
const filesFromIdx = argv.indexOf("--files-from");

if (filesIdx !== -1) {
  // Collect all values after --files until the next -- flag or end
  explicitFiles = [];
  for (let i = filesIdx + 1; i < argv.length; i++) {
    if (argv[i].startsWith("--")) break;
    explicitFiles.push(argv[i]);
  }
} else if (filesFromIdx !== -1) {
  const listPath = argv[filesFromIdx + 1];
  try {
    explicitFiles = fs
      .readFileSync(listPath, "utf8")
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean);
  } catch (e) {
    process.stderr.write(`[ts_analyzer] cannot read --files-from ${listPath}: ${e.message}\n`);
    explicitFiles = [];
  }
}

// ---------------------------------------------------------------------------
// File collection
// ---------------------------------------------------------------------------
const EXCLUDED_DIRS = new Set([
  "node_modules", ".git", "dist", "build", "target",
  ".next", ".nuxt", "out", "coverage", "__pycache__",
]);

const SOURCE_EXTS = new Set([".ts", ".tsx", ".js", ".jsx"]);

function walkDir(dir, results = []) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (e) {
    process.stderr.write(`[ts_analyzer] cannot read dir ${dir}: ${e.message}\n`);
    return results;
  }
  for (const entry of entries) {
    if (entry.name.startsWith(".") || EXCLUDED_DIRS.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walkDir(full, results);
    } else if (entry.isFile()) {
      const ext = path.extname(entry.name).toLowerCase();
      if (SOURCE_EXTS.has(ext)) results.push(full);
    }
  }
  return results;
}

function resolveTargetFiles() {
  if (explicitFiles !== null) {
    return explicitFiles
      .map((f) => path.join(repoRoot, f))
      .filter((p) => {
        if (!fs.existsSync(p)) {
          process.stderr.write(`[ts_analyzer] not found: ${p}\n`);
          return false;
        }
        return true;
      });
  }
  return walkDir(repoRoot);
}

// ---------------------------------------------------------------------------
// Helper utilities
// ---------------------------------------------------------------------------
function getRelPath(absPath) {
  return path.relative(repoRoot, absPath).replace(/\\/g, "/");
}

/**
 * Derive the file-based FQN prefix from a relative path.
 * Strips the extension and normalises separators.
 * e.g. "src/services/AuthService.ts" -> "src/services/AuthService"
 */
function filePrefix(relPath) {
  const { dir, name } = path.posix.parse(relPath.replace(/\\/g, "/"));
  return dir ? `${dir}/${name}` : name;
}

/**
 * Build an import map for the source file:
 *   local_name -> { modulePath, exportedName }
 *
 * Examples:
 *   import { AuthService } from './auth'  -> { AuthService: { modulePath: './auth', exportedName: 'AuthService' } }
 *   import React from 'react'            -> { React: { modulePath: 'react', exportedName: 'default' } }
 *   import * as Utils from './utils'     -> { Utils: { modulePath: './utils', exportedName: '*' } }
 */
function buildImportMap(sourceFile) {
  const map = new Map();
  for (const decl of sourceFile.getImportDeclarations()) {
    const modSpec = decl.getModuleSpecifierValue();
    // default import
    const defaultImport = decl.getDefaultImport();
    if (defaultImport) {
      map.set(defaultImport.getText(), { modulePath: modSpec, exportedName: "default" });
    }
    // namespace import: import * as X
    const namespaceImport = decl.getNamespaceImport();
    if (namespaceImport) {
      map.set(namespaceImport.getText(), { modulePath: modSpec, exportedName: "*" });
    }
    // named imports: import { A, B as C }
    for (const named of decl.getNamedImports()) {
      const localName = named.getAliasNode()
        ? named.getAliasNode().getText()
        : named.getName();
      map.set(localName, { modulePath: modSpec, exportedName: named.getName() });
    }
  }
  return map;
}

/**
 * Collect identifiers used in function/method call expressions and resolve
 * them against the import map.  Returns a deduplicated sorted array of FQN
 * strings (module-path + exported name, or bare module-path for namespace
 * imports).
 */
function collectCallsFromNode(node, importMap) {
  const seen = new Set();
  const callExpressions = node.getDescendantsOfKind
    ? node.getDescendantsOfKind(SyntaxKind.CallExpression)
    : [];

  for (const call of callExpressions) {
    const expr = call.getExpression();
    // foo() or foo.bar()
    let rootName = null;
    if (expr.getKind() === SyntaxKind.Identifier) {
      rootName = expr.getText();
    } else if (expr.getKind() === SyntaxKind.PropertyAccessExpression) {
      // Walk to the leftmost identifier
      let cur = expr;
      while (cur.getKind && cur.getKind() === SyntaxKind.PropertyAccessExpression) {
        cur = cur.getExpression();
      }
      if (cur.getKind && cur.getKind() === SyntaxKind.Identifier) {
        rootName = cur.getText();
      }
    }
    if (rootName && importMap.has(rootName)) {
      const { modulePath, exportedName } = importMap.get(rootName);
      const fqn =
        exportedName === "*" || exportedName === "default"
          ? modulePath
          : `${modulePath}.${exportedName}`;
      seen.add(fqn);
    }
  }
  return Array.from(seen).sort();
}

/**
 * Also collect all import module paths as calls (coarser but always present).
 */
function allImportPaths(sourceFile) {
  return sourceFile
    .getImportDeclarations()
    .map((d) => d.getModuleSpecifierValue());
}

// ---------------------------------------------------------------------------
// Analysis
// ---------------------------------------------------------------------------
const project = new Project({
  addFilesFromTsConfig: false,
  skipFileDependencyResolution: true,
  compilerOptions: {
    allowJs: true,
    resolveJsonModule: false,
    noEmit: true,
  },
});

const results = [];

for (const filePath of resolveTargetFiles()) {
  try {
    const sf = project.addSourceFileAtPath(filePath);
    const relPath = getRelPath(filePath);
    const prefix = filePrefix(relPath);
    const importMap = buildImportMap(sf);
    const importPaths = allImportPaths(sf);

    // -----------------------------------------------------------------------
    // Classes
    // -----------------------------------------------------------------------
    for (const cls of sf.getClasses()) {
      const name = cls.getName() || "<anonymous>";
      const fqn = `${prefix}.${name}`;

      const methods = cls.getMethods().map((m) => {
        const params = m
          .getParameters()
          .map((p) => {
            const typeText = p.getTypeNode() ? `: ${p.getTypeNode().getText()}` : "";
            return `${p.getName()}${typeText}`;
          })
          .join(", ");
        return {
          name: m.getName(),
          signature: `${m.getName()}(${params})`,
          loc_start: m.getStartLineNumber(),
          loc_end: Math.max(m.getStartLineNumber(), m.getEndLineNumber()),
        };
      });

      const calls = collectCallsFromNode(cls, importMap);

      results.push({
        fqn,
        file_path: relPath,
        loc_start: cls.getStartLineNumber(),
        loc_end: Math.max(cls.getStartLineNumber(), cls.getEndLineNumber()),
        node_type: "class",
        calls: calls.length > 0 ? calls : importPaths,
        methods,
      });
    }

    // -----------------------------------------------------------------------
    // Interfaces
    // -----------------------------------------------------------------------
    for (const iface of sf.getInterfaces()) {
      const name = iface.getName();
      const fqn = `${prefix}.${name}`;

      const methods = iface.getMethods().map((m) => {
        const params = m
          .getParameters()
          .map((p) => {
            const typeText = p.getTypeNode() ? `: ${p.getTypeNode().getText()}` : "";
            return `${p.getName()}${typeText}`;
          })
          .join(", ");
        return {
          name: m.getName(),
          signature: `${m.getName()}(${params})`,
          loc_start: m.getStartLineNumber(),
          loc_end: Math.max(m.getStartLineNumber(), m.getEndLineNumber()),
        };
      });

      results.push({
        fqn,
        file_path: relPath,
        loc_start: iface.getStartLineNumber(),
        loc_end: Math.max(iface.getStartLineNumber(), iface.getEndLineNumber()),
        node_type: "class",
        calls: importPaths,
        methods,
      });
    }

    // -----------------------------------------------------------------------
    // Top-level functions
    // -----------------------------------------------------------------------
    for (const fn of sf.getFunctions()) {
      const name = fn.getName() || "<anonymous>";
      const fqn = `${prefix}.${name}`;

      const params = fn
        .getParameters()
        .map((p) => {
          const typeText = p.getTypeNode() ? `: ${p.getTypeNode().getText()}` : "";
          return `${p.getName()}${typeText}`;
        })
        .join(", ");

      const calls = collectCallsFromNode(fn, importMap);

      results.push({
        fqn,
        file_path: relPath,
        loc_start: fn.getStartLineNumber(),
        loc_end: Math.max(fn.getStartLineNumber(), fn.getEndLineNumber()),
        node_type: "function",
        calls: calls.length > 0 ? calls : importPaths,
        methods: [],
      });
    }

    // -----------------------------------------------------------------------
    // Top-level arrow functions / variable declarations that are functions
    // -----------------------------------------------------------------------
    for (const varDecl of sf.getVariableDeclarations()) {
      const init = varDecl.getInitializer();
      if (!init) continue;
      const kind = init.getKind();
      if (
        kind !== SyntaxKind.ArrowFunction &&
        kind !== SyntaxKind.FunctionExpression
      ) {
        continue;
      }
      const name = varDecl.getName();
      const fqn = `${prefix}.${name}`;

      const params = init.getParameters
        ? init
            .getParameters()
            .map((p) => {
              const typeText = p.getTypeNode() ? `: ${p.getTypeNode().getText()}` : "";
              return `${p.getName()}${typeText}`;
            })
            .join(", ")
        : "";

      const calls = collectCallsFromNode(init, importMap);

      results.push({
        fqn,
        file_path: relPath,
        loc_start: varDecl.getStartLineNumber(),
        loc_end: Math.max(varDecl.getStartLineNumber(), varDecl.getEndLineNumber()),
        node_type: "function",
        calls: calls.length > 0 ? calls : importPaths,
        methods: [],
      });
    }
  } catch (e) {
    process.stderr.write(`[ts_analyzer] error processing ${filePath}: ${e.message}\n`);
  }
}

process.stdout.write(JSON.stringify(results));
