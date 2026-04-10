package com.back.backend.domain.github.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 장기 방치된 repo clone 디렉터리를 주기적으로 정리하는 스케줄러.
 *
 * <h3>동작 시점</h3>
 * 매일 새벽 3시 (UTC 기준). 서버가 한국 시간대라면 오전 3시에 실행.
 *
 * <h3>정리 대상</h3>
 * {@code {analysis.repo-base-path}/{userId}/{repoId}/} 구조의 디렉터리 중
 * 마지막 수정 시각이 {@code RETENTION_HOURS} 이상 경과한 것.
 *
 * <h3>보존 조건</h3>
 * AI 분석 실패 시 clone을 즉시 삭제하지 않으므로, 이 스케줄러가 최종 청소를 담당한다.
 * 성공한 경우에는 {@link AnalysisPipelineService}가 즉시 삭제하므로 여기서 처리할 것이 없다.
 */
@Component
public class CloneDirectoryCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CloneDirectoryCleanupScheduler.class);
    private static final long RETENTION_HOURS = 24;

    private final Path cloneBaseDir;

    public CloneDirectoryCleanupScheduler(
            @Value("${analysis.repo-base-path:/data/repos}") String baseDir) {
        this.cloneBaseDir = Paths.get(baseDir);
    }

    /**
     * 매일 새벽 3시: 24시간 이상 방치된 clone 디렉터리를 삭제한다.
     *
     * <p>디렉터리 구조: {@code {baseDir}/{userId}/{repoId}/}
     * lastModifiedTime 기준으로 RETENTION_HOURS 경과 여부를 판단한다.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanStaleCloneDirectories() {
        if (!Files.exists(cloneBaseDir)) {
            log.debug("CloneDirectoryCleanup: base dir not found, skipping. path={}", cloneBaseDir);
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(RETENTION_HOURS));
        int deletedCount = 0;
        int errorCount = 0;

        try (Stream<Path> userDirs = Files.list(cloneBaseDir)) {
            for (Path userDir : userDirs.toList()) {
                if (!Files.isDirectory(userDir)) continue;

                try (Stream<Path> repoDirs = Files.list(userDir)) {
                    for (Path repoDir : repoDirs.toList()) {
                        if (!Files.isDirectory(repoDir)) continue;

                        try {
                            FileTime lastModified = Files.getLastModifiedTime(repoDir);
                            if (lastModified.toInstant().isBefore(cutoff)) {
                                deleteRecursively(repoDir);
                                deletedCount++;
                                log.info("CloneCleanup: deleted stale dir: {}", repoDir);
                            }
                        } catch (IOException e) {
                            errorCount++;
                            log.warn("CloneCleanup: failed to process {}: {}", repoDir, e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    log.warn("CloneCleanup: failed to list repoDirs under {}: {}", userDir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("CloneCleanup: failed to list cloneBaseDir {}: {}", cloneBaseDir, e.getMessage(), e);
            return;
        }

        log.info("CloneDirectoryCleanup completed: deleted={}, errors={}", deletedCount, errorCount);
    }

    private void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        log.warn("CloneCleanup: delete failed: {}", p);
                    }
                });
        }
    }
}
