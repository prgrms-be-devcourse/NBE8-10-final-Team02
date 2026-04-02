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
 * <h3>주요 동작</h3>
 * <ol>
 *   <li>커밋 헤더(sha, subject, body) 전체를 한 번의 git log 호출로 수집</li>
 *   <li>{@link CommitClassifier}로 IGNORED 커밋 제거</li>
 *   <li>남은 커밋 중 상위 {@code maxCommits}개만 diff 조회</li>
 * </ol>
 *
 * evidenceBullets / challenges / techDecisions 세분화는 AI에 위임한다.
 * 이 서비스의 역할은 "소재 없는 커밋을 제거해 토큰 예산 내에 더 많은 실질 커밋을 담는 것"이다.
 *
 * <h3>주의</h3>
 * <ul>
 *   <li>GitHub primary email 1개만 사용 (설계 §2.3)</li>
 *   <li>merge commit 제외 (--no-merges)</li>
 *   <li>diff 1개 최대 크기: {@value #MAX_DIFF_CHARS} 문자</li>
 * </ul>
 */
@Service
public class ContributionExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ContributionExtractorService.class);

    private static final int MAX_DIFF_CHARS = 40_000;
    private static final int GIT_TIMEOUT_SECONDS = 120;

    private final CommitClassifier classifier;

    public ContributionExtractorService(CommitClassifier classifier) {
        this.classifier = classifier;
    }

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
            if (!trimmed.isEmpty()) files.add(trimmed);
        }

        log.info("Found {} authored files for author={}", files.size(), authorEmail);
        return files;
    }

    /**
     * IGNORED 커밋을 제거한 뒤 상위 {@code maxCommits}개의 diff를 반환한다.
     *
     * <p>전략:
     * <ol>
     *   <li>커밋 헤더(sha/subject/body) 전체를 가볍게 수집</li>
     *   <li>IGNORED 제거 (docs/chore/style/ci/test/build)</li>
     *   <li>남은 커밋 상위 maxCommits개만 diff 조회 — 토큰 예산 준수</li>
     * </ol>
     *
     * @param repoPath    로컬 클론 경로
     * @param authorEmail 본인 GitHub primary email
     * @param maxCommits  diff를 가져올 최대 커밋 수
     */
    public List<DiffEntry> getFilteredDiffs(Path repoPath, String authorEmail, int maxCommits) {
        log.info("Fetching filtered diffs: repoPath={}, author={}, maxCommits={}",
                repoPath, authorEmail, maxCommits);

        // 1. 커밋 헤더 전체 수집 (diff 없음 — 가볍다)
        List<CommitHeader> headers = getAllCommitHeaders(repoPath, authorEmail);
        log.info("Total commits for author={}: {}", authorEmail, headers.size());

        // 2. IGNORED 제거 후 상위 maxCommits 선택
        List<CommitHeader> included = headers.stream()
                .filter(h -> classifier.classify(h.subject()) == CommitCategory.INCLUDED)
                .limit(maxCommits)
                .toList();

        log.info("After IGNORED filter: {} → {} commits (limit={})",
                headers.size(), included.size(), maxCommits);

        // 3. diff 조회
        List<DiffEntry> diffs = new ArrayList<>();
        for (CommitHeader h : included) {
            diffs.add(new DiffEntry(h.sha(), h.subject(), h.body(), getCommitDiff(repoPath, h.sha())));
        }

        return diffs;
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    /**
     * git log에서 SHA + subject + body를 전체 수집한다.
     * NUL(x00) / US(x1f) 구분자로 멀티라인 body를 안전하게 파싱한다.
     * 포맷: %x00%h%x1f%s%x1f%b
     */
    private List<CommitHeader> getAllCommitHeaders(Path repoPath, String authorEmail) {
        List<String> command = List.of(
                "git", "log",
                "--author=" + authorEmail,
                "--no-merges",
                "--format=%x00%h%x1f%s%x1f%b"
        );

        String raw = runGit(command, repoPath);
        List<CommitHeader> headers = new ArrayList<>();

        for (String record : raw.split("\0")) {
            if (record.isBlank()) continue;
            String[] parts = record.split("\u001f", 3);
            String sha     = parts[0].trim();
            String subject = parts.length > 1 ? parts[1].trim() : "";
            String body    = parts.length > 2 ? parts[2].trim() : "";

            if (!sha.isEmpty() && !subject.isEmpty()) {
                headers.add(new CommitHeader(sha, subject, body));
            }
        }

        return headers;
    }

    private String getCommitDiff(Path repoPath, String sha) {
        List<String> command = Arrays.asList("git", "diff", sha + "^", sha);
        try {
            String diff = runGit(command, repoPath);
            if (diff.length() > MAX_DIFF_CHARS) {
                return diff.substring(0, MAX_DIFF_CHARS) + "\n... [truncated]";
            }
            return diff;
        } catch (Exception e) {
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

    private record CommitHeader(String sha, String subject, String body) {}
}
