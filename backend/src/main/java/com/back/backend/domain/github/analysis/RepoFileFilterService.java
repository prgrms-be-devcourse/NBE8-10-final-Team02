package com.back.backend.domain.github.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * clone된 repo에서 분석 대상 파일을 필터링한다.
 *
 * <p>두 가지 기준으로 파일을 제외한다:</p>
 * <ul>
 *   <li>확장자가 허용 목록(Whitelist)에 없는 경우 — 바이너리, 미디어, 압축 파일 등</li>
 *   <li>단일 파일 크기가 설정 상한(기본 1MB)을 초과하는 경우</li>
 * </ul>
 *
 * <p>.sh 파일은 terraform 등 IaC 스크립트를 포함하므로 허용한다.</p>
 */
@Service
public class RepoFileFilterService {

    private static final Logger log = LoggerFactory.getLogger(RepoFileFilterService.class);

    /** 단일 파일 크기 기본 상한 (1MB). */
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 1L * 1024 * 1024;

    /**
     * 허용 확장자 Whitelist.
     * 소스코드, 스크립트(.sh 포함), 설정, 문서, 빌드 파일을 포함한다.
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        // JVM 계열
        ".java", ".kt", ".kts", ".scala", ".groovy",
        // 웹/Node
        ".js", ".ts", ".jsx", ".tsx", ".mjs", ".cjs", ".vue", ".svelte",
        // 스크립트 (.sh는 terraform 등 IaC 포함)
        ".py", ".rb", ".php", ".go", ".rs", ".c", ".cpp", ".h", ".hpp",
        ".cs", ".swift", ".sh", ".bash",
        // 설정/문서
        ".yaml", ".yml", ".json", ".toml", ".xml", ".properties",
        ".md", ".txt", ".rst", ".csv",
        // 빌드/IaC
        ".gradle", ".gradle.kts", ".tf", ".tfvars",
        ".dockerfile",
        // 데이터베이스
        ".sql"
    );

    private final long maxFileSizeBytes;

    public RepoFileFilterService(
            @Value("${analysis.file-filter.max-file-size-bytes:" + DEFAULT_MAX_FILE_SIZE_BYTES + "}")
            long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * repo 루트를 재귀 탐색해 허용 파일 목록과 제외 파일 목록을 반환한다.
     *
     * @param repoRoot clone된 repo의 루트 경로
     * @return 필터링 결과
     */
    public FilterResult filter(Path repoRoot) {
        List<Path> allowed = new ArrayList<>();
        List<SkippedFile> skipped = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String reason = classify(file);
                if (reason == null) {
                    allowed.add(file);
                } else {
                    skipped.add(new SkippedFile(file, reason));
                    log.info("File skipped [{}]: {}", reason, repoRoot.relativize(file));
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk repo directory: {}, reason: {}", repoRoot, e.getMessage());
        }

        log.info("RepoFileFilter result: allowed={}, skipped={}, repoRoot={}",
                allowed.size(), skipped.size(), repoRoot.getFileName());
        return new FilterResult(allowed, skipped);
    }

    /**
     * 파일이 허용되면 null, 제외 사유가 있으면 해당 사유 문자열을 반환한다.
     */
    private String classify(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();

        // Dockerfile은 확장자 없이 파일명 자체로 허용
        if (fileName.equals("dockerfile")) {
            return null;
        }

        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx < 0) {
            // 확장자 없는 파일은 제외 (숨김 파일, 바이너리 등)
            return "no_extension";
        }

        String ext = fileName.substring(dotIdx);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return "extension_blocked";
        }

        try {
            long size = Files.size(file);
            if (size > maxFileSizeBytes) {
                return "size_exceeded";
            }
        } catch (IOException e) {
            log.debug("Could not read file size for {}: {}", file, e.getMessage());
            return "size_read_failed";
        }

        return null;
    }

    // ─────────────────────────────────────────────────
    // 결과 타입
    // ─────────────────────────────────────────────────

    /**
     * 필터링 결과.
     *
     * @param allowed 분석 대상 파일 목록
     * @param skipped 제외된 파일 목록 (경로 + 사유)
     */
    public record FilterResult(List<Path> allowed, List<SkippedFile> skipped) {}

    /**
     * 제외된 파일 정보.
     *
     * @param path   파일 경로
     * @param reason 제외 사유 ("extension_blocked" | "size_exceeded" | "no_extension" | "size_read_failed")
     */
    public record SkippedFile(Path path, String reason) {}
}
