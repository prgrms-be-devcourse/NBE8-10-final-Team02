package com.back.backend.domain.github.analysis;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 분석 파이프라인용 repo clone/fetch 서비스.
 *
 * Clone 전략: blobless (--filter=blob:none)
 *   - commit/tree 이력 전체 보유 → git log --author 기여 추출 가능
 *   - blob은 checkout 시 lazy fetch → 외부 대형 repo 용량 절감
 *
 * Clone 경로: {repoBasePath}/{userId}/{repositoryId}/
 * 이미 존재하면 git fetch + checkout origin/HEAD로 갱신한다.
 */
@Service
public class RepoCloneService {

    private static final Logger log = LoggerFactory.getLogger(RepoCloneService.class);
    private static final int CLONE_TIMEOUT_MINUTES = 15;
    private static final int FETCH_TIMEOUT_MINUTES = 5;

    private final String repoBasePath;

    public RepoCloneService(@Value("${analysis.repo-base-path:/data/repos}") String repoBasePath) {
        this.repoBasePath = repoBasePath;
    }

    /**
     * repo를 clone하거나 기존 clone을 최신으로 갱신한다.
     *
     * @param repoUrl      GitHub repo URL (예: https://github.com/owner/repo)
     * @param userId       소유자 userId (경로 분리용)
     * @param repositoryId DB의 github_repositories.id
     * @return clone된 repo의 로컬 Path
     */
    public Path cloneOrFetch(String repoUrl, Long userId, Long repositoryId) {
        Path repoPath = buildRepoPath(userId, repositoryId);

        if (Files.exists(repoPath.resolve(".git"))) {
            log.info("Fetching existing clone: userId={}, repoId={}", userId, repositoryId);
            try {
                fetch(repoPath);
                return repoPath;
            } catch (ServiceException e) {
                // fetch 실패(lock 파일, 손상된 clone 등) → 디렉토리 삭제 후 fresh clone 재시도
                log.warn("Fetch failed ({}), removing for fresh clone: userId={}, repoId={}",
                        e.getMessage(), userId, repositoryId);
                forceDelete(repoPath);
            }
        } else if (Files.exists(repoPath)) {
            // 이전 실패 run이 남긴 불완전한 디렉토리(no .git) 정리
            log.warn("Stale directory found (no .git), removing before clone: {}", repoPath);
            forceDelete(repoPath);
        }

        // 삭제가 실패한 경우 clone이 "already exists"로 실패하는 대신 명확한 오류를 냄
        if (Files.exists(repoPath)) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "이전 분석 디렉토리 삭제 실패 — 잠시 후 다시 시도해주세요: " + repoPath.getFileName());
        }

        log.info("Cloning repo: {} → {}", repoUrl, repoPath);
        clone(repoUrl, repoPath);
        return repoPath;
    }

    /**
     * clone된 repo 경로를 반환한다. clone이 없으면 예외를 던진다.
     */
    public Path getRepoPath(Long userId, Long repositoryId) {
        Path repoPath = buildRepoPath(userId, repositoryId);
        if (!Files.exists(repoPath.resolve(".git"))) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "repo clone이 존재하지 않습니다. cloneOrFetch를 먼저 호출하세요.");
        }
        return repoPath;
    }

    // ─────────────────────────────────────────────────
    // 내부 git 명령
    // ─────────────────────────────────────────────────

    private void clone(String repoUrl, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "clone 디렉토리 생성 실패: " + e.getMessage());
        }

        runGit(
                List.of("git", "clone", "--filter=blob:none", repoUrl, targetPath.toString()),
                targetPath.getParent(),
                CLONE_TIMEOUT_MINUTES
        );
    }

    private void fetch(Path repoPath) {
        runGit(
                List.of("git", "fetch", "origin"),
                repoPath,
                FETCH_TIMEOUT_MINUTES
        );
        runGit(
                List.of("git", "checkout", "origin/HEAD", "--detach"),
                repoPath,
                FETCH_TIMEOUT_MINUTES
        );
    }

    private void runGit(List<String> command, Path workingDir, int timeoutMinutes) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true);

            process = pb.start();
            String output = readOutput(process);

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                        "git 명령 타임아웃: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("git command failed: cmd={} exitCode={} output=[{}]", command, exitCode, output);
                throw new ServiceException(ErrorCode.GITHUB_COMMIT_SYNC_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                        "git 명령 실패 (exit " + exitCode + "): " + command.get(1)
                        + (output.isBlank() ? "" : " — " + output.lines().findFirst().orElse("")));
            }

            log.debug("git {} success: {}", command.get(1), workingDir);

        } catch (ServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly(); // git 프로세스 즉시 종료
            Thread.currentThread().interrupt();
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "git 명령이 취소되었습니다: " + command.get(1));
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "git 명령 실행 중 오류: " + e.getMessage());
        }
    }

    private String readOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 파이프라인 완료 후 clone 디렉토리를 삭제한다.
     * 실패해도 파이프라인에 영향을 주지 않는다.
     */
    public void deleteRepo(Long userId, Long repositoryId) {
        Path repoPath = buildRepoPath(userId, repositoryId);
        if (!Files.exists(repoPath)) return;
        forceDelete(repoPath);
        if (Files.exists(repoPath)) {
            log.warn("Could not fully delete repo clone (some files remain): userId={}, repoId={}", userId, repositoryId);
        } else {
            log.info("Deleted repo clone: userId={}, repoId={}", userId, repositoryId);
        }
    }

    /**
     * 디렉토리를 강제 삭제한다.
     *
     * 1단계: Java Files.walk로 파일 순차 삭제 (일반적인 경우)
     * 2단계: 디렉토리가 남아 있으면 OS 명령(rm -rf)으로 재시도 (git lock 파일 등 잔여물 대응)
     */
    private void forceDelete(Path path) {
        // 1단계: Java 삭제
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try { Files.delete(p); }
                      catch (IOException e) { log.debug("Java delete failed for {}: {}", p, e.getMessage()); }
                  });
        } catch (IOException e) {
            log.warn("Files.walk failed for {}: {}", path, e.getMessage());
        }

        // 2단계: 아직 남아 있으면 OS rm -rf 재시도
        if (Files.exists(path)) {
            log.warn("Directory still exists after Java delete, retrying with rm -rf: {}", path);
            try {
                Process process = new ProcessBuilder("rm", "-rf", path.toString())
                        .redirectErrorStream(true)
                        .start();
                process.waitFor(30, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("rm -rf fallback failed for {}: {}", path, e.getMessage());
            }
        }
    }

    public Path buildRepoPath(Long userId, Long repositoryId) {
        return Path.of(repoBasePath, userId.toString(), repositoryId.toString());
    }
}
