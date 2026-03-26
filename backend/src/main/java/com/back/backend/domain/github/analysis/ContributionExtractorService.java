package com.back.backend.domain.github.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * git log --author 기반 본인 기여 추출 서비스.
 *
 * 추출 내용:
 *   - 본인이 수정한 파일 목록 (authoredFiles)
 *   - 커밋별 diff (ContributionDiff → RepoSummary 생성 시 증거 자료)
 *
 * 주의:
 *   - GitHub primary email 1개만 사용한다 (설계 §2.3).
 *   - merge commit은 제외한다 (--no-merges).
 *   - diff 크기가 크면 토큰 예산 초과 → TokenBudgetEnforcer에서 잘라낸다.
 */
@Service
public class ContributionExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ContributionExtractorService.class);

    // diff 1개 최대 크기 (문자 수 기준, ~40KB)
    private static final int MAX_DIFF_CHARS = 40_000;
    private static final int GIT_TIMEOUT_SECONDS = 120;

    /**
     * 본인이 수정한 파일 목록을 반환한다 (repo 루트 기준 상대 경로).
     * 외부 repo는 이 파일들만 AST 분석 대상으로 한정한다.
     */
    public Set<String> getAuthoredFiles(Path repoPath, String authorEmail) {
        log.info("Extracting authored files: repoPath={}, author={}", repoPath, authorEmail);

        List<String> command = List.of(
                "git", "log",
                "--author=" + authorEmail,
                "--no-merges",
                "--name-only",
                "--format="
        );

        String output = runGit(command, repoPath);

        Set<String> files = new LinkedHashSet<>();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                files.add(trimmed);
            }
        }

        log.info("Found {} authored files for author={}", files.size(), authorEmail);
        return files;
    }

    /**
     * 본인 커밋의 diff 목록을 반환한다.
     * RepoSummary 생성 시 LLM에 기여 증거로 제공된다.
     *
     * @param maxCommits 최대 커밋 수 (토큰 예산 제한)
     */
    public List<DiffEntry> getContributionDiffs(Path repoPath, String authorEmail, int maxCommits) {
        log.info("Extracting contribution diffs: repoPath={}, author={}, maxCommits={}",
                repoPath, authorEmail, maxCommits);

        // 1. 본인 커밋 SHA + subject 조회
        List<String> logCommand = List.of(
                "git", "log",
                "--author=" + authorEmail,
                "--no-merges",
                "--format=%h %s",
                "-n", String.valueOf(maxCommits)
        );
        String logOutput = runGit(logCommand, repoPath);

        List<DiffEntry> diffs = new ArrayList<>();
        for (String line : logOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            int spaceIdx = trimmed.indexOf(' ');
            if (spaceIdx < 0) continue;
            String sha = trimmed.substring(0, spaceIdx);
            String subject = trimmed.substring(spaceIdx + 1);

            // 2. 커밋별 diff 조회
            String diff = getCommitDiff(repoPath, sha);
            diffs.add(new DiffEntry(sha, subject, diff));
        }

        log.info("Extracted {} diffs for author={}", diffs.size(), authorEmail);
        return diffs;
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    private String getCommitDiff(Path repoPath, String sha) {
        List<String> command = Arrays.asList(
                "git", "diff", sha + "^", sha
        );
        try {
            String diff = runGit(command, repoPath);
            // 크기 제한
            if (diff.length() > MAX_DIFF_CHARS) {
                return diff.substring(0, MAX_DIFF_CHARS) + "\n... [truncated]";
            }
            return diff;
        } catch (Exception e) {
            // 최초 커밋(parent 없음) 등의 경우 graceful fallback
            List<String> showCommand = List.of("git", "show", "--stat", sha);
            try {
                return runGit(showCommand, repoPath);
            } catch (Exception ex) {
                log.warn("Failed to get diff for sha={}: {}", sha, ex.getMessage());
                return "";
            }
        }
    }

    private String runGit(List<String> command, Path workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(false);

            Process process = pb.start();

            // stdout 읽기
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("git command timed out: {}", command);
                return "";
            }

            return output;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("git command failed: {}, reason: {}", command, e.getMessage());
            return "";
        }
    }
}
