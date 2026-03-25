package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.NodeType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 언어별 정적 분석 디스패처.
 *
 * - Java:       JavaStaticAnalyzer (JavaParser, JVM 내부 실행)
 * - Kotlin:     kotlin_analyzer.py (regex 기반)
 * - Python:     python_analyzer.py (ast stdlib)
 * - JS/TS:      ts_analyzer.js (ts-morph)
 * - Go:         go_analyzer.py (regex 기반)
 * - Rust:       rust_analyzer.py (regex 기반)
 * - C/C++:      c_analyzer.py (regex 기반)
 *
 * 외부 스크립트 미설치 시 해당 언어 파일은 건너뛰고 경고 로그를 남긴다.
 */
@Service
public class StaticAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StaticAnalysisService.class);
    private static final int SCRIPT_TIMEOUT_MINUTES = 10;

    private final JavaStaticAnalyzer javaAnalyzer;
    private final AnalysisDaemonManager daemonManager;
    private final ObjectMapper objectMapper;
    private final String scriptsBasePath;

    public StaticAnalysisService(
            JavaStaticAnalyzer javaAnalyzer,
            AnalysisDaemonManager daemonManager,
            ObjectMapper objectMapper,
            @Value("${analysis.scripts-base-path:/opt/analysis-scripts}") String scriptsBasePath
    ) {
        this.javaAnalyzer = javaAnalyzer;
        this.daemonManager = daemonManager;
        this.objectMapper = objectMapper;
        this.scriptsBasePath = scriptsBasePath;
    }

    /**
     * repo에 존재하는 모든 언어에 대해 분석기를 실행한다.
     *
     * 주요 언어 하나만 골라 실행하는 방식에서 변경:
     * Java + Kotlin 혼합 repo(Spring + Gradle .kts 등)처럼 언어가 섞인 경우에도 누락 없이 분석한다.
     * 각 언어는 해당 파일이 1개 이상 존재할 때만 분석기를 실행한다.
     *
     * @param repoRoot    repo clone 루트 경로
     * @param targetFiles 분석 대상 파일 (empty=전체, present=본인 기여 파일만)
     * @return 분석된 코드 노드 목록 (언어별 결과 합산)
     */
    public List<AnalysisNode> analyze(Path repoRoot, Optional<Set<String>> targetFiles) {
        // Files.walk 1회로 repo 내 존재하는 확장자 전체를 수집 — 언어별 중복 탐색 제거
        Set<String> presentExts = collectPresentExtensions(repoRoot);
        if (presentExts.isEmpty()) {
            log.warn("No source files found in repo: {}", repoRoot);
            return List.of();
        }

        List<AnalysisNode> result = new ArrayList<>();

        runIfPresent(result, repoRoot, targetFiles, presentExts, new String[]{".java"},
                (root, files) -> analyzeJava(root, files), "Java");
        runIfPresent(result, repoRoot, targetFiles, presentExts, new String[]{".kt", ".kts"},
                (root, files) -> analyzeWithScript(root, files, "kotlin_analyzer.py", "python3"), "Kotlin");
        runIfPresent(result, repoRoot, targetFiles, presentExts, new String[]{".py"},
                (root, files) -> analyzeWithScript(root, files, "python_analyzer.py", "python3"), "Python");
        runIfPresent(result, repoRoot, targetFiles, presentExts, new String[]{".ts", ".tsx", ".js", ".jsx"},
                (root, files) -> analyzeWithScript(root, files, "ts_analyzer.js", "node"), "TS/JS");
        runIfPresent(result, repoRoot, targetFiles, presentExts, new String[]{".go"},
                (root, files) -> analyzeWithScript(root, files, "go_analyzer.py", "python3"), "Go");
        runIfPresent(result, repoRoot, targetFiles, presentExts, new String[]{".rs"},
                (root, files) -> analyzeWithScript(root, files, "rust_analyzer.py", "python3"), "Rust");
        runIfPresent(result, repoRoot, targetFiles, presentExts, new String[]{".cpp", ".cc", ".cxx", ".hpp", ".c", ".h"},
                (root, files) -> analyzeWithScript(root, files, "c_analyzer.py", "python3"), "C/C++");

        if (result.isEmpty()) {
            log.warn("No supported source files found in repo: {}", repoRoot);
        }
        return result;
    }

    // ─────────────────────────────────────────────────
    // 언어 감지
    // ─────────────────────────────────────────────────

    public Language detectPrimaryLanguage(Path repoRoot) {
        long javaCount = 0, ktCount = 0, pyCount = 0, jsCount = 0, tsCount = 0,
             goCount = 0, rustCount = 0, cCount = 0, cppCount = 0;

        try (var stream = Files.walk(repoRoot, 5)) {
            var files = stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .toList();

            javaCount = files.stream().filter(n -> n.endsWith(".java")).count();
            ktCount   = files.stream().filter(n -> n.endsWith(".kt") || n.endsWith(".kts")).count();
            pyCount   = files.stream().filter(n -> n.endsWith(".py")).count();
            tsCount   = files.stream().filter(n -> n.endsWith(".ts") || n.endsWith(".tsx")).count();
            jsCount   = files.stream().filter(n -> n.endsWith(".js") || n.endsWith(".jsx")).count();
            goCount   = files.stream().filter(n -> n.endsWith(".go")).count();
            rustCount = files.stream().filter(n -> n.endsWith(".rs")).count();
            cppCount  = files.stream().filter(n -> n.endsWith(".cpp") || n.endsWith(".cc")
                                                || n.endsWith(".cxx") || n.endsWith(".hpp")).count();
            cCount    = files.stream().filter(n -> n.endsWith(".c") || n.endsWith(".h")).count();
        } catch (IOException e) {
            log.warn("Language detection failed: {}", e.getMessage());
        }

        // JS + TS를 합산해 비교 (같은 스크립트로 처리)
        long scriptCount = jsCount + tsCount;

        long max = Math.max(
                Math.max(Math.max(javaCount, ktCount), Math.max(pyCount, scriptCount)),
                Math.max(Math.max(goCount, rustCount), Math.max(cCount, cppCount))
        );
        if (max == 0) return Language.UNKNOWN;
        if (max == javaCount)   return Language.JAVA;
        if (max == ktCount)     return Language.KOTLIN;
        if (max == pyCount)     return Language.PYTHON;
        if (max == scriptCount) return tsCount >= jsCount ? Language.TYPESCRIPT : Language.JAVASCRIPT;
        if (max == goCount)     return Language.GO;
        if (max == rustCount)   return Language.RUST;
        if (max == cppCount)    return Language.CPP;
        return Language.C;
    }

    public enum Language { JAVA, KOTLIN, PYTHON, JAVASCRIPT, TYPESCRIPT, GO, RUST, C, CPP, UNKNOWN }

    // ─────────────────────────────────────────────────
    // 언어별 분석 / 파일 존재 여부 / 확장자 필터
    // ─────────────────────────────────────────────────

    @FunctionalInterface
    private interface Analyzer {
        List<AnalysisNode> run(Path repoRoot, Optional<Set<String>> files);
    }

    /**
     * repo에 해당 확장자 파일이 존재하고, targetFiles 필터 결과도 비어 있지 않을 때만 분석기를 실행한다.
     *
     * @param presentExts analyze() 진입 시 1회 수집한 repo 내 확장자 집합 (Files.walk 재사용)
     */
    private void runIfPresent(List<AnalysisNode> result, Path repoRoot,
                               Optional<Set<String>> targetFiles, Set<String> presentExts,
                               String[] exts, Analyzer analyzer, String langLabel) {
        // 수집된 확장자 집합으로 판단 — Files.walk 추가 호출 없음
        if (Arrays.stream(exts).noneMatch(presentExts::contains)) return;

        Optional<Set<String>> filtered = filterByExt(targetFiles, exts);
        // targetFiles가 present인데 필터 결과가 비어있으면 해당 언어에 본인 기여 없음 → 스킵
        if (targetFiles.isPresent() && filtered.isEmpty()) return;

        log.info("Running {} analyzer for repo: {}", langLabel, repoRoot);
        result.addAll(analyzer.run(repoRoot, filtered));
    }

    /**
     * repo 내 파일 확장자 집합을 Files.walk 1회로 수집한다.
     * 이후 모든 언어 존재 여부 확인에 재사용하여 중복 I/O를 제거한다.
     */
    private Set<String> collectPresentExtensions(Path repoRoot) {
        try (var stream = Files.walk(repoRoot, 5)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        String name = p.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        return dot >= 0 ? name.substring(dot) : "";
                    })
                    .filter(ext -> !ext.isEmpty())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.warn("Failed to collect file extensions from repo: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * targetFiles를 주어진 확장자들로 필터링한다.
     * targetFiles가 empty(전체 분석)이면 그대로 반환 — 각 분석기가 repoRoot에서 직접 해당 확장자 파일을 찾는다.
     * targetFiles가 present이면 해당 확장자 파일만 남긴다. 결과가 비어 있으면 empty 반환(분석기 미실행).
     */
    private Optional<Set<String>> filterByExt(Optional<Set<String>> targetFiles, String... exts) {
        if (targetFiles.isEmpty()) return Optional.empty();
        Set<String> filtered = targetFiles.get().stream()
                .filter(path -> {
                    for (String ext : exts) {
                        if (path.endsWith(ext)) return true;
                    }
                    return false;
                })
                .collect(Collectors.toSet());
        return filtered.isEmpty() ? Optional.empty() : Optional.of(filtered);
    }

    private List<AnalysisNode> analyzeJava(Path repoRoot, Optional<Set<String>> targetFiles) {
        return javaAnalyzer.analyzeAll(repoRoot, targetFiles);
    }

    /**
     * 외부 스크립트로 분석을 실행한다.
     *
     * 실행 우선순위:
     *   1. AnalysisDaemonManager — 상주 프로세스에 JSON 요청 전송 (기동 비용 없음)
     *   2. ProcessBuilder 폴백 — 데몬 미기동 또는 응답 실패 시 프로세스 직접 실행
     *
     * 스크립트 호출 형식 (프로세스 방식):
     *   python3 {script} {repoRoot} [--files file1 file2 ...]
     *   node    {script} {repoRoot} [--files file1 file2 ...]
     */
    private List<AnalysisNode> analyzeWithScript(
            Path repoRoot, Optional<Set<String>> targetFiles,
            String scriptName, String interpreter
    ) {
        // 1. 데몬 시도
        String langKey = scriptNameToLangKey(scriptName);
        String daemonResponse = daemonManager.callRaw(langKey, repoRoot, targetFiles);
        if (daemonResponse != null) {
            return parseScriptOutput(daemonResponse);
        }

        // 2. 프로세스 폴백
        Path scriptPath = Path.of(scriptsBasePath, scriptName);
        if (!Files.exists(scriptPath)) {
            log.warn("Analysis script not found: {}. Skipping static analysis.", scriptPath);
            return List.of();
        }

        List<String> command = new ArrayList<>(List.of(interpreter, scriptPath.toString(),
                repoRoot.toString()));

        targetFiles.ifPresent(files -> {
            command.add("--files");
            command.addAll(files);
        });

        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(false);

            Process process = pb.start();
            String stdout;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                stdout = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(SCRIPT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Analysis script timed out: {}", scriptName);
                return List.of();
            }

            if (process.exitValue() != 0) {
                log.warn("Analysis script failed (exit {}): {}", process.exitValue(), scriptName);
                return List.of();
            }

            return parseScriptOutput(stdout);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to run analysis script {}: {}", scriptName, e.getMessage());
            return List.of();
        }
    }

    /** 스크립트 파일명 → AnalysisDaemonManager 언어 키 변환 */
    private static String scriptNameToLangKey(String scriptName) {
        return switch (scriptName) {
            case "kotlin_analyzer.py" -> "kotlin";
            case "python_analyzer.py" -> "python";
            case "ts_analyzer.js"     -> "ts";
            case "go_analyzer.py"     -> "go";
            case "rust_analyzer.py"   -> "rust";
            case "c_analyzer.py"      -> "c";
            default                   -> scriptName;
        };
    }

    /**
     * 외부 스크립트 출력 JSON을 AnalysisNode 목록으로 변환한다.
     *
     * 스크립트 출력 형식:
     * [{"fqn":"module.ClassName","file_path":"src/...","loc_start":1,"loc_end":50,
     *   "node_type":"class","calls":["other.Module"],"methods":[...]}]
     */
    private List<AnalysisNode> parseScriptOutput(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<ScriptNodeDto> dtos = objectMapper.readValue(json,
                    new TypeReference<List<ScriptNodeDto>>() {});
            return dtos.stream().map(this::toAnalysisNode).toList();
        } catch (Exception e) {
            log.warn("Failed to parse analysis script output: {}", e.getMessage());
            return List.of();
        }
    }

    // ─────────────────────────────────────────────────
    // 외부 스크립트 출력 파싱 DTO
    // ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScriptNodeDto(
            String fqn,
            String file_path,
            int loc_start,
            int loc_end,
            String node_type,
            List<String> calls,
            List<ScriptMethodDto> methods
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScriptMethodDto(
            String name,
            String signature,
            int loc_start,
            int loc_end
    ) {}

    private AnalysisNode toAnalysisNode(ScriptNodeDto dto) {
        NodeType nodeType;
        try {
            nodeType = NodeType.fromValue(dto.node_type());
        } catch (IllegalArgumentException e) {
            nodeType = NodeType.FUNCTION;
        }

        List<AnalysisNode.MethodInfo> methods = dto.methods() == null ? List.of() :
                dto.methods().stream()
                        .map(m -> new AnalysisNode.MethodInfo(m.name(), m.signature(),
                                m.loc_start(), m.loc_end()))
                        .toList();

        return new AnalysisNode(
                dto.fqn(),
                dto.file_path(),
                dto.loc_start(),
                dto.loc_end(),
                nodeType,
                dto.calls() == null ? List.of() : dto.calls(),
                methods
        );
    }
}
