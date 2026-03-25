#!/usr/bin/env node
/**
 * TypeScript/JavaScript 소스 정적 분석기.
 * ts-morph를 사용하여 클래스/함수 구조와 호출 관계를 추출한다.
 *
 * 사용법:
 *   node ts_analyzer.js {repoRoot} [--files file1 file2 ...]
 *
 * 설치 요구사항:
 *   npm install -g ts-morph
 */
const path = require("path");
const fs = require("fs");

// ts-morph 없으면 빈 배열 출력 후 종료
let Project;
try {
  ({ Project } = require("ts-morph"));
} catch (e) {
  process.stdout.write("[]");
  process.exit(0);
}

const args = process.argv.slice(2);
const repoRoot = args[0];
const filesIdx = args.indexOf("--files");
const targetFiles =
  filesIdx >= 0 ? args.slice(filesIdx + 1) : null;

const project = new Project({ addFilesFromTsConfig: false });

function getRelPath(absPath) {
  return path.relative(repoRoot, absPath).replace(/\\/g, "/");
}

function collectFiles() {
  if (targetFiles) {
    return targetFiles.map((f) => path.join(repoRoot, f)).filter(fs.existsSync);
  }
  const result = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith(".") || entry.name === "node_modules") continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (/\.(ts|tsx|js|jsx)$/.test(entry.name)) result.push(full);
    }
  }
  walk(repoRoot);
  return result;
}

const results = [];

for (const filePath of collectFiles()) {
  try {
    const sf = project.addSourceFileAtPath(filePath);
    const relPath = getRelPath(filePath);

    sf.getClasses().forEach((cls) => {
      const fqn = [sf.getBaseNameWithoutExtension(), cls.getName()]
        .filter(Boolean)
        .join(".");

      const methods = cls.getMethods().map((m) => ({
        name: m.getName(),
        signature: `${m.getName()}(${m.getParameters()
          .map((p) => p.getType().getText())
          .join(",")})`,
        loc_start: m.getStartLineNumber(),
        loc_end: m.getEndLineNumber(),
      }));

      // 호출 관계: import 목록에서 추출
      const calls = sf
        .getImportDeclarations()
        .map((i) => i.getModuleSpecifierValue())
        .filter((s) => !s.startsWith(".") || s.length > 1);

      results.push({
        fqn,
        file_path: relPath,
        loc_start: cls.getStartLineNumber(),
        loc_end: cls.getEndLineNumber(),
        node_type: "class",
        calls,
        methods,
      });
    });
  } catch (e) {
    // 파싱 실패 파일 건너뜀
  }
}

process.stdout.write(JSON.stringify(results));
