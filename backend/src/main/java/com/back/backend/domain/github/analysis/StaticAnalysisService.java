package com.back.backend.domain.github.analysis;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 언어별 정적 분석 디스패처.
 *
 * - Java:       JavaStaticAnalyzer (JavaParser, JVM 내부 실행)
 * - Kotlin:     kotlin_analyzer.py (tree-sitter-kotlin)
 * - Python:     python_analyzer.py (ast stdlib, no deps)
 * - JS/TS:      ts_analyzer.js (ts-morph)
 * - Go:         go_analyzer.py (tree-sitter-go)
 * - Rust:       rust_analyzer.py (tree-sitter-rust)
 * - C/C++:      c_analyzer.py (tree-sitter-c / tree-sitter-cpp)
 *
 * 외부 스크립트 미설치 시 해당 언어 파일은 건너뛰고 경고 로그를 남긴다.
 */
@Service
public class StaticAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StaticAnalysisService.class);
    private static final int SCRIPT_TIMEOUT_MINUTES = 10;

    private final JavaStaticAnalyzer javaAnalyzer;
    private final AnalysisDaemonManager daemonManager;
    private final LanguageDetectionService languageDetectionService;
    private final ObjectMapper objectMapper;
    private final String scriptsBasePath;

    public StaticAnalysisService(
            JavaStaticAnalyzer javaAnalyzer,
            AnalysisDaemonManager daemonManager,
            LanguageDetectionService languageDetectionService,
            ObjectMapper objectMapper,
            @Value("${analysis.scripts-base-path:/opt/analysis-scripts}") String scriptsBasePath
    ) {
        this.javaAnalyzer = javaAnalyzer;
        this.daemonManager = daemonManager;
        this.languageDetectionService = languageDetectionService;
        this.objectMapper = objectMapper;
        this.scriptsBasePath = scriptsBasePath;
    }

    /**
     * repo에 존재하는 모든 언어에 대해 분석기를 실행한다.
     *
     * LanguageDetectionService(tokei 우선, 폴백=확장자 스캔)로 언어별 파일 목록을 먼저 구한 뒤,
     * 존재하는 언어의 분석기만 선택적으로 실행한다.
     *
     * @param repoRoot    repo clone 루트 경로
     * @param targetFiles 분석 대상 파일 (empty=전체, present=본인 기여 파일만)
     * @return 분석된 코드 노드 목록 (언어별 결과 합산)
     */
    public List<AnalysisNode> analyze(Path repoRoot, Optional<Set<String>> targetFiles) {
        Map<String, Set<String>> detected = languageDetectionService.detect(repoRoot);
        if (detected.isEmpty()) {
            log.warn("No supported source files found in repo: {}", repoRoot);
            return List.of();
        }

        log.info("Detected languages: {} in repo: {}", detected.keySet(), repoRoot);

        List<AnalysisNode> result = new ArrayList<>();

        runIfDetected(result, repoRoot, targetFiles, detected, "java",
                (root, files) -> analyzeJava(root, files), "Java");
        runIfDetected(result, repoRoot, targetFiles, detected, "kotlin",
                (root, files) -> analyzeWithScript(root, files, "kotlin_analyzer.py", "python3"), "Kotlin");
        runIfDetected(result, repoRoot, targetFiles, detected, "python",
                (root, files) -> analyzeWithScript(root, files, "python_analyzer.py", "python3"), "Python");
        runIfDetected(result, repoRoot, targetFiles, detected, "ts",
                (root, files) -> analyzeWithScript(root, files, "ts_analyzer.js", "node"), "TS/JS");
        runIfDetected(result, repoRoot, targetFiles, detected, "go",
                (root, files) -> analyzeWithScript(root, files, "go_analyzer.py", "python3"), "Go");
        runIfDetected(result, repoRoot, targetFiles, detected, "rust",
                (root, files) -> analyzeWithScript(root, files, "rust_analyzer.py", "python3"), "Rust");
        runIfDetected(result, repoRoot, targetFiles, detected, "c",
                (root, files) -> analyzeWithScript(root, files, "c_analyzer.py", "python3"), "C/C++");

        if (result.isEmpty()) {
            log.warn("All analyzers returned empty results for repo: {}", repoRoot);
        }
        return result;
    }

    // ─────────────────────────────────────────────────
    // 언어별 분석 디스패치
    // ─────────────────────────────────────────────────

    @FunctionalInterface
    private interface Analyzer {
        List<AnalysisNode> run(Path repoRoot, Optional<Set<String>> files);
    }

    /**
     * detected 맵에 langKey가 있을 때만 분석기를 실행한다.
     *
     * targetFiles가 present(large repo)이면 detected 파일 목록과 교집합을 구해 분석 범위를 좁힌다.
     * 교집합이 비어 있으면 해당 언어에 본인 기여 없음 → 스킵.
     */
    private void runIfDetected(List<AnalysisNode> result, Path repoRoot,
                                Optional<Set<String>> targetFiles,
                                Map<String, Set<String>> detected,
                                String langKey, Analyzer analyzer, String langLabel) {
        Set<String> langFiles = detected.get(langKey);
        if (langFiles == null || langFiles.isEmpty()) return;

        Optional<Set<String>> scope;
        if (targetFiles.isEmpty()) {
            // 전체 분석 — tokei가 찾은 파일 목록을 분석기에 전달
            scope = Optional.of(langFiles);
        } else {
            // large repo — 본인 기여 파일과 교집합
            Set<String> intersection = targetFiles.get().stream()
                    .filter(langFiles::contains)
                    .collect(Collectors.toSet());
            if (intersection.isEmpty()) return; // 해당 언어에 기여 없음
            scope = Optional.of(intersection);
        }

        log.info("Running {} analyzer ({} files) for repo: {}", langLabel, scope.get().size(), repoRoot);
        result.addAll(analyzer.run(repoRoot, scope));
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

        // 파일 목록이 있을 때: 파일 수에 따라 인라인 vs 임시 파일 방식 선택
        // 인라인(--files): 인자 수가 적을 때 단순
        // 임시 파일(--files-from): 수백~수천 개 파일 → ARG_MAX 초과 방지
        Path tempFileList = null;
        if (targetFiles.isPresent()) {
            Set<String> files = targetFiles.get();
            if (files.size() <= 200) {
                command.add("--files");
                command.addAll(files);
            } else {
                try {
                    tempFileList = Files.createTempFile("analysis-files-", ".txt");
                    Files.write(tempFileList, files, StandardCharsets.UTF_8);
                    command.add("--files-from");
                    command.add(tempFileList.toString());
                } catch (IOException e) {
                    log.warn("Failed to write temp file list, falling back to inline args: {}", e.getMessage());
                    command.add("--files");
                    command.addAll(files);
                }
            }
        }

        final Path finalTempFileList = tempFileList;
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
        } finally {
            if (finalTempFileList != null) {
                try { Files.deleteIfExists(finalTempFileList); } catch (IOException ignored) {}
            }
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
