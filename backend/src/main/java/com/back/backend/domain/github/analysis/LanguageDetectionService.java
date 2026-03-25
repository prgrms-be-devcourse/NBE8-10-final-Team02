package com.back.backend.domain.github.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 레포지토리의 언어 구성을 탐지한다.
 *
 * tokei 바이너리가 설치되어 있으면 한 번 호출로 언어별 파일 목록을 얻는다.
 * tokei가 없으면 Files.walk 기반 확장자 스캔으로 폴백한다.
 *
 * 반환값: langKey → 해당 언어 파일의 repoRoot 기준 상대 경로 집합
 *   langKey: "java" | "kotlin" | "python" | "ts" | "go" | "rust" | "c"
 */
@Service
public class LanguageDetectionService {

    private static final Logger log = LoggerFactory.getLogger(LanguageDetectionService.class);
    private static final int TOKEI_TIMEOUT_SECONDS = 30;

    // tokei가 출력하는 언어 이름 → 우리 내부 langKey
    private static final Map<String, String> TOKEI_TO_KEY = Map.ofEntries(
            Map.entry("Java",          "java"),
            Map.entry("Kotlin",        "kotlin"),
            Map.entry("Python",        "python"),
            Map.entry("TypeScript",    "ts"),
            Map.entry("JavaScript",    "ts"),
            Map.entry("TSX",           "ts"),
            Map.entry("JSX",           "ts"),
            Map.entry("Go",            "go"),
            Map.entry("Rust",          "rust"),
            Map.entry("C",             "c"),
            Map.entry("C++",           "c"),
            Map.entry("C Header",      "c"),
            Map.entry("C++ Header",    "c")
    );

    // 폴백용: langKey → 파일 확장자 목록
    private static final Map<String, String[]> KEY_TO_EXTS = Map.of(
            "java",   new String[]{".java"},
            "kotlin", new String[]{".kt", ".kts"},
            "python", new String[]{".py"},
            "ts",     new String[]{".ts", ".tsx", ".js", ".jsx"},
            "go",     new String[]{".go"},
            "rust",   new String[]{".rs"},
            "c",      new String[]{".c", ".h", ".cpp", ".cc", ".cxx", ".hpp"}
    );

    private final ObjectMapper objectMapper;

    public LanguageDetectionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 레포지토리에 존재하는 언어별 파일 경로 맵을 반환한다.
     *
     * @param repoRoot repo clone 루트
     * @return langKey → repoRoot 기준 상대 경로 집합 (파일이 없는 언어는 포함되지 않음)
     */
    public Map<String, Set<String>> detect(Path repoRoot) {
        Map<String, Set<String>> result = tryTokei(repoRoot);
        if (result != null) {
            log.debug("Language detection via tokei: {} languages found", result.size());
            return result;
        }
        log.debug("tokei unavailable, falling back to extension scan");
        return extensionScan(repoRoot);
    }

    /**
     * 감지된 언어 중 코드 라인 수(또는 파일 수)가 가장 많은 언어를 반환한다.
     */
    public String detectPrimary(Map<String, Set<String>> detected) {
        return detected.entrySet().stream()
                .max(Map.Entry.comparingByValue(java.util.Comparator.comparingInt(Set::size)))
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    // ─────────────────────────────────────────────────
    // tokei 호출
    // ─────────────────────────────────────────────────

    private Map<String, Set<String>> tryTokei(Path repoRoot) {
        try {
            Process process = new ProcessBuilder("tokei", "--output", "json", repoRoot.toString())
                    .redirectErrorStream(false)
                    .start();

            String stdout;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                stdout = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(TOKEI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("tokei timed out");
                return null;
            }
            if (process.exitValue() != 0 || stdout.isBlank()) return null;

            return parseTokeiOutput(stdout, repoRoot);

        } catch (IOException e) {
            // tokei 미설치 — 정상적인 폴백 경로
            log.debug("tokei not found: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Map<String, Set<String>> parseTokeiOutput(String json, Path repoRoot) {
        try {
            Map<String, TokeiLang> raw = objectMapper.readValue(json,
                    new TypeReference<Map<String, TokeiLang>>() {});

            Map<String, Set<String>> result = new HashMap<>();
            for (Map.Entry<String, TokeiLang> entry : raw.entrySet()) {
                String langKey = TOKEI_TO_KEY.get(entry.getKey());
                if (langKey == null) continue; // 지원하지 않는 언어

                List<TokeiReport> reports = entry.getValue().reports();
                if (reports == null || reports.isEmpty()) continue;

                Set<String> paths = result.computeIfAbsent(langKey, k -> new HashSet<>());
                for (TokeiReport report : reports) {
                    if (report.name() == null) continue;
                    Path abs = Path.of(report.name());
                    // repoRoot 기준 상대 경로로 변환
                    try {
                        paths.add(repoRoot.relativize(abs).toString().replace("\\", "/"));
                    } catch (IllegalArgumentException e) {
                        paths.add(report.name()); // 상대화 실패 시 절대 경로 그대로 사용
                    }
                }
            }
            return result;

        } catch (Exception e) {
            log.warn("Failed to parse tokei output: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────
    // 폴백: 확장자 기반 스캔
    // ─────────────────────────────────────────────────

    // 빌드 결과물·의존성 캐시 디렉토리 — 분석 대상에서 제외
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "target", "build", ".gradle", "out",           // JVM 빌드
            "node_modules", ".next", "dist", ".cache",      // JS/TS 빌드
            ".git", ".idea", ".vscode",                     // IDE/VCS
            "__pycache__", ".tox", ".venv", "venv",         // Python
            "vendor"                                         // Go/Rust
    );

    private Map<String, Set<String>> extensionScan(Path repoRoot) {
        Map<String, Set<String>> result = new HashMap<>();
        try (var stream = Files.walk(repoRoot, 8)) {
            stream
                .filter(path -> !isExcluded(repoRoot, path))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    for (Map.Entry<String, String[]> entry : KEY_TO_EXTS.entrySet()) {
                        for (String ext : entry.getValue()) {
                            if (fileName.endsWith(ext)) {
                                String rel = repoRoot.relativize(path).toString().replace("\\", "/");
                                result.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(rel);
                                return; // 첫 번째 매칭 언어로만 분류
                            }
                        }
                    }
                });
        } catch (IOException e) {
            log.warn("Extension scan failed: {}", e.getMessage());
        }
        return result;
    }

    private boolean isExcluded(Path repoRoot, Path path) {
        // repoRoot 직하위 또는 하위 임의 깊이의 제외 디렉토리 이름 확인
        for (int i = repoRoot.getNameCount(); i < path.getNameCount(); i++) {
            if (EXCLUDED_DIRS.contains(path.getName(i).toString())) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────
    // tokei JSON 파싱 DTO
    // ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokeiLang(List<TokeiReport> reports) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokeiReport(String name) {}
}
