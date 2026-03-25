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
            fetch(repoPath);
        } else {
            log.info("Cloning repo: {} → {}", repoUrl, repoPath);
            clone(repoUrl, repoPath);
        }

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
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true);

            Process process = pb.start();
            String output = readOutput(process);

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                        "git 명령 타임아웃: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("git command failed: {} exitCode={} output={}", command, exitCode, output);
                throw new ServiceException(ErrorCode.GITHUB_COMMIT_SYNC_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                        "git 명령 실패 (exit " + exitCode + "): " + command.get(1));
            }

            log.debug("git {} success: {}", command.get(1), workingDir);

        } catch (ServiceException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
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
        try {
            deleteRecursively(repoPath);
            log.info("Deleted repo clone: userId={}, repoId={}", userId, repositoryId);
        } catch (IOException e) {
            log.warn("Failed to delete repo clone: userId={}, repoId={}, error={}", userId, repositoryId, e.getMessage());
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try { Files.delete(p); }
                      catch (IOException e) { log.warn("Failed to delete: {}", p); }
                  });
        }
    }

    public Path buildRepoPath(Long userId, Long repositoryId) {
        return Path.of(repoBasePath, userId.toString(), repositoryId.toString());
    }
}
