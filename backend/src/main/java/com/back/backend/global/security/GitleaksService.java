package com.back.backend.global.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Gitleaks CLI를 안전하게 실행하는 서비스.
 *
 * <p>두 가지 사용 시나리오를 지원한다:</p>
 * <ul>
 *   <li>Repo 스캔: clone된 디렉토리 전체를 스캔 (git 모드)</li>
 *   <li>Document 스캔: 텍스트를 임시 파일에 써서 {@code --no-git} 모드로 스캔</li>
 * </ul>
 *
 * <h3>자원 제어</h3>
 * <ul>
 *   <li>BoundedExecutor (core=1, max=2, queue=5) — 동시 스캔을 2개로 제한</li>
 *   <li>프로세스 타임아웃 — 설정값(기본 60초) 초과 시 강제 종료</li>
 *   <li>큐 초과 시: 스캔을 스킵하고 경고 로그만 남김 (분석은 계속)</li>
 * </ul>
 *
 * <h3>보안 로그 원칙</h3>
 * <p>발견된 시크릿의 실제 값(match)은 절대 로그에 남기지 않는다.
 * 로그에는 파일 경로와 ruleId만 기록한다.</p>
 */
@Service
public class GitleaksService {

    private static final Logger log = LoggerFactory.getLogger(GitleaksService.class);

    private static final int EXECUTOR_CORE    = 1;
    private static final int EXECUTOR_MAX     = 2;
    private static final int EXECUTOR_QUEUE   = 5;
    private static final int KEEP_ALIVE_SEC   = 30;

    private final String binaryPath;
    private final int timeoutSeconds;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    /** 동시 스캔 수를 제한하는 bounded executor. */
    private final ThreadPoolExecutor executor;

    public GitleaksService(
            @Value("${security.gitleaks.binary-path:gitleaks}") String binaryPath,
            @Value("${security.gitleaks.timeout-seconds:60}")   int timeoutSeconds,
            @Value("${security.gitleaks.enabled:true}")         boolean enabled,
            ObjectMapper objectMapper) {
        this.binaryPath     = binaryPath;
        this.timeoutSeconds = timeoutSeconds;
        this.enabled        = enabled;
        this.objectMapper   = objectMapper;
        this.executor = new ThreadPoolExecutor(
                EXECUTOR_CORE, EXECUTOR_MAX,
                KEEP_ALIVE_SEC, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(EXECUTOR_QUEUE),
                r -> {
                    Thread t = new Thread(r, "gitleaks-scanner");
                    t.setDaemon(true);
                    return t;
                },
                // 큐 초과 시 CallerRunsPolicy 대신 DiscardPolicy — 스캔 스킵으로 처리
                new ThreadPoolExecutor.DiscardPolicy()
        );
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    // ─────────────────────────────────────────────────
    // 공개 API
    // ─────────────────────────────────────────────────

    /**
     * clone된 repo 디렉토리를 git 모드로 스캔한다.
     *
     * <p>gitleaks가 비활성화(enabled=false)되어 있으면 빈 결과를 반환한다.</p>
     *
     * @param repoDir clone된 repo 루트 경로
     * @return 스캔 결과
     */
    public GitleaksScanResult scanRepo(Path repoDir) {
        if (!enabled) {
            log.debug("Gitleaks disabled, skipping repo scan: {}", repoDir.getFileName());
            return GitleaksScanResult.empty();
        }
        return runScan(repoDir, false);
    }

    /**
     * 텍스트를 임시 파일에 기록하고 {@code --no-git} 모드로 스캔한다.
     * 임시 파일은 스캔 완료 후 반드시 삭제된다.
     *
     * @param text 스캔할 텍스트 (null 또는 빈 값이면 빈 결과 반환)
     * @return 스캔 결과
     */
    public GitleaksScanResult scanText(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return GitleaksScanResult.empty();
        }

        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("gitleaks-doc-");
            Path tmpFile = tmpDir.resolve("content.txt");
            Files.writeString(tmpFile, text);
            return runScan(tmpDir, true);
        } catch (IOException e) {
            log.warn("Failed to create temp file for gitleaks text scan: {}", e.getMessage());
            return GitleaksScanResult.empty();
        } finally {
            deleteTmpDir(tmpDir);
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 스캔 실행
    // ─────────────────────────────────────────────────

    /**
     * gitleaks 프로세스를 executor에 제출하고 결과를 반환한다.
     * 큐가 가득 차 DiscardPolicy가 발동되면 executor는 태스크를 무시한다.
     * 이를 감지하기 위해 Future를 직접 사용한다.
     */
    private GitleaksScanResult runScan(Path targetDir, boolean noGit) {
        try {
            return executor.submit(() -> executeScan(targetDir, noGit)).get(timeoutSeconds + 5L, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("Gitleaks scan queue full, skipping scan for: {}", targetDir.getFileName());
            return GitleaksScanResult.empty();
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Gitleaks scan submit timed out for: {}", targetDir.getFileName());
            return GitleaksScanResult.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Gitleaks scan interrupted for: {}", targetDir.getFileName());
            return GitleaksScanResult.empty();
        } catch (Exception e) {
            log.warn("Gitleaks scan failed for {}: {}", targetDir.getFileName(), e.getMessage());
            return GitleaksScanResult.empty();
        }
    }

    /**
     * 실제 gitleaks 프로세스를 실행하고 JSON 결과를 파싱한다.
     * gitleaks exit code 1 = 시크릿 발견 (정상적인 감지 결과).
     * exit code 2 이상 = 실행 오류.
     */
    private GitleaksScanResult executeScan(Path targetDir, boolean noGit) {
        Path reportFile = null;
        try {
            reportFile = Files.createTempFile("gitleaks-report-", ".json");

            List<String> cmd = buildCommand(targetDir, reportFile, noGit);
            log.debug("Running gitleaks: {}", cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(targetDir.toFile())
                    .redirectErrorStream(true);

            Process process = pb.start();

            // stdout을 drain하지 않으면 OS 파이프 버퍼 초과 시 deadlock 발생
            drainOutput(process);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Gitleaks timed out ({}s) for: {}", timeoutSeconds, targetDir.getFileName());
                return GitleaksScanResult.empty();
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                // 시크릿 없음
                return GitleaksScanResult.empty();
            }
            if (exitCode == 1) {
                // 시크릿 발견 — 리포트 파싱
                return parseReport(reportFile);
            }
            // exit code 2+ = gitleaks 실행 오류
            log.warn("Gitleaks exited with error code {} for: {}", exitCode, targetDir.getFileName());
            return GitleaksScanResult.empty();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Gitleaks process interrupted for: {}", targetDir.getFileName());
            return GitleaksScanResult.empty();
        } catch (IOException e) {
            log.warn("Gitleaks IO error for {}: {}", targetDir.getFileName(), e.getMessage());
            return GitleaksScanResult.empty();
        } finally {
            deleteTmpFile(reportFile);
        }
    }

    private List<String> buildCommand(Path targetDir, Path reportFile, boolean noGit) {
        List<String> cmd = new java.util.ArrayList<>(List.of(
                binaryPath, "detect",
                "--source", targetDir.toAbsolutePath().toString(),
                "--report-format", "json",
                "--report-path", reportFile.toAbsolutePath().toString(),
                "--exit-code", "1",
                "--no-banner",
                "--log-level", "warn"
        ));
        if (noGit) {
            cmd.add("--no-git");
        }
        return cmd;
    }

    /** stdout을 백그라운드 스레드에서 drain한다 (deadlock 방지). */
    private void drainOutput(Process process) {
        Thread drainer = new Thread(() -> {
            try (var in = process.getInputStream()) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            } catch (IOException ignored) {}
        }, "gitleaks-drain");
        drainer.setDaemon(true);
        drainer.start();
    }

    private GitleaksScanResult parseReport(Path reportFile) throws IOException {
        if (!Files.exists(reportFile) || Files.size(reportFile) == 0) {
            return GitleaksScanResult.empty();
        }

        List<GitleaksRawFinding> raw = objectMapper.readValue(
                reportFile.toFile(),
                new TypeReference<>() {}
        );

        List<SecretFinding> findings = raw.stream()
                .map(r -> new SecretFinding(
                        r.file() != null ? r.file() : "unknown",
                        r.ruleId() != null ? r.ruleId() : "unknown",
                        r.description() != null ? r.description() : ""
                        // !! r.secret() 는 절대 포함하지 않는다
                ))
                .toList();

        log.info("Gitleaks found {} secret(s). Files: {}",
                findings.size(),
                findings.stream().map(SecretFinding::filePath).distinct().toList());

        return new GitleaksScanResult(true, findings);
    }

    // ─────────────────────────────────────────────────
    // 임시 파일 정리
    // ─────────────────────────────────────────────────

    private void deleteTmpFile(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("Failed to delete temp file {}: {}", file, e.getMessage());
        }
    }

    private void deleteTmpDir(Path dir) {
        if (dir == null) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            log.debug("Failed to delete temp dir {}: {}", dir, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    // 결과 타입
    // ─────────────────────────────────────────────────

    /**
     * gitleaks 스캔 결과.
     *
     * @param hasFindings 시크릿 발견 여부
     * @param findings    발견 목록 (파일 경로 + 룰 ID + 설명만 포함, 실제 값 제외)
     */
    public record GitleaksScanResult(boolean hasFindings, List<SecretFinding> findings) {
        public static GitleaksScanResult empty() {
            return new GitleaksScanResult(false, Collections.emptyList());
        }
    }

    /**
     * 개별 시크릿 발견 항목.
     * 실제 시크릿 값(match)은 보안상 저장하지 않는다.
     *
     * @param filePath    발견된 파일 경로 (상대 경로)
     * @param ruleId      gitleaks 룰 ID (예: "generic-api-key")
     * @param description 사람이 읽을 수 있는 설명
     */
    public record SecretFinding(String filePath, String ruleId, String description) {}

    /** gitleaks JSON 리포트 역직렬화용 내부 DTO. match 필드는 의도적으로 읽지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitleaksRawFinding(
            @JsonProperty("File")        String file,
            @JsonProperty("RuleID")      String ruleId,
            @JsonProperty("Description") String description
            // "Secret", "Match" 필드는 의도적으로 제외 — 시크릿 값 로깅 방지
    ) {}
}
