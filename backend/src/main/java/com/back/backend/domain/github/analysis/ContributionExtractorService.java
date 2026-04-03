package com.back.backend.domain.github.analysis;

import com.back.backend.domain.github.entity.CodeIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * git log --author 기반 본인 기여 추출 서비스.
 *
 * <h3>주요 동작</h3>
 * <ol>
 *   <li>커밋 헤더(sha, subject, body, date) + numstat(파일별 추가/삭제 줄 수)을 한 번의 git log로 수집</li>
 *   <li>{@link CommitClassifier}로 IGNORED 커밋 제거</li>
 *   <li>커밋 수 기준 Early/Mid/Late 1/3 시간 분산 (§7.5)</li>
 *   <li>각 구간에서 스코어링(§7.4) + 도메인 다양성 패널티로 상위 커밋 선택</li>
 *   <li>선택된 커밋만 diff 조회 — 토큰 예산 준수</li>
 * </ol>
 *
 * <h3>스코어링 요소 (§7.4)</h3>
 * <ul>
 *   <li>prefix 점수 (feat/perf/fix 등)</li>
 *   <li>body 존재 (+5, techDecisions 후보)</li>
 *   <li>라인 수 log scale (+log10(lines+1)*3, 거대 커밋 과대평가 방지)</li>
 *   <li>키워드-라인 역비례 보너스: subject/body에 면접 키워드 + diff ≤ 50줄 (+6)</li>
 *   <li>건드린 파일의 최고 PageRank (*8, CodeIndex에서 조회)</li>
 *   <li>핵심 파일 비율 (Service/Controller/Repository/Domain *10)</li>
 * </ul>
 *
 * <h3>도메인 다양성 (§7.5)</h3>
 * 변경 파일 경로의 상위 2단계 디렉터리를 버킷으로 삼아 버킷당 최대 2개로 제한한다.
 */
@Service
public class ContributionExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ContributionExtractorService.class);

    /** diff가 이 크기를 초과하면 raw diff 대신 CodeIndex 기반 요약으로 대체한다 (§7.7) */
    private static final int DIFF_COMPRESSION_THRESHOLD = 10_000;
    private static final int GIT_TIMEOUT_SECONDS  = 120;
    private static final int MAX_BUCKET_COMMITS   = 2;

    // ─── 스코어링 패턴 ───

    private static final Pattern PREFIX_FEAT =
            Pattern.compile("^(feat|perf|implement|add)[:(\\[]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX_FIX =
            Pattern.compile("^(fix|resolve|hotfix|bugfix)[:(\\[]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX_REFACTOR =
            Pattern.compile("^refactor[:(\\[]", Pattern.CASE_INSENSITIVE);

    /** 면접 단골 키워드 — subject/body에서만 탐지 (diff 코드 내 변수명 오탐 방지) */
    private static final Pattern INTERVIEW_KEYWORD_PATTERN = Pattern.compile(
            "\\b(lock|synchronized|atomic|concurrent|race|semaphore|volatile|"
            + "cache|index|batch|paging|n\\+1|query.optim|"
            + "migration|flyway|liquibase|\\bddl\\b|schema|"
            + "transactional|isolation|rollback|"
            + "oauth|payment|webhook|webclient|resttemplate|httpclient|"
            + "circuitbreaker|retry|fallback|timeout|dead.?letter|"
            + "jwt|csrf|xss|injection|security|"
            + "kafka|rabbitmq|eventlistener|pub.?sub|"
            + "pagination|streaming|bulk|partitioning|rate.?limit|"
            + "strategy|facade|decorator|cqrs|\\bddd\\b)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CORE_FILE_PATTERN =
            Pattern.compile("(Service|Controller|Repository|Domain|Entity|UseCase)\\.java$",
                    Pattern.CASE_INSENSITIVE);

    /** CodeIndex.methods JSON에서 signature 값을 추출하는 패턴 */
    private static final Pattern METHOD_SIGNATURE_PATTERN =
            Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    private final CommitClassifier classifier;

    public ContributionExtractorService(CommitClassifier classifier) {
        this.classifier = classifier;
    }

    // ─────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────

    /**
     * 본인이 수정한 파일 목록을 반환한다 (repo 루트 기준 상대 경로).
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
     * IGNORED 제거 → 시간 분산(§7.5) → 스코어링+도메인 다양성(§7.4/§7.5)으로
     * 선택된 커밋의 diff를 반환한다.
     *
     * @param repoPath    로컬 클론 경로
     * @param authorEmail 본인 GitHub primary email
     * @param maxCommits  최종 diff를 가져올 최대 커밋 수
     * @param codeEntries CodeIndex 목록 — 파일별 PageRank 조회에 사용
     */
    public List<DiffEntry> getFilteredDiffs(Path repoPath, String authorEmail,
                                            int maxCommits, List<CodeIndex> codeEntries) {
        log.info("Fetching filtered diffs: repoPath={}, author={}, maxCommits={}",
                repoPath, authorEmail, maxCommits);

        // 1. 커밋 헤더 + numstat 수집 (diff 없음 — 가볍다)
        List<CommitHeader> headers = getAllCommitHeaders(repoPath, authorEmail);
        log.info("Total commits for author={}: {}", authorEmail, headers.size());

        // 2. IGNORED 제거 (git log는 newest-first)
        List<CommitHeader> included = headers.stream()
                .filter(h -> classifier.classify(h.subject()) == CommitCategory.INCLUDED)
                .toList();
        log.info("After IGNORED filter: {} → {} commits", headers.size(), included.size());

        // 3. CodeIndex에서 파일 → PageRank 맵, 파일 → CodeIndex 맵 구성
        Map<String, Double> pageRankByFile = codeEntries.stream()
                .filter(e -> e.getPagerank() != null)
                .collect(Collectors.toMap(
                        CodeIndex::getFilePath,
                        CodeIndex::getPagerank,
                        Math::max));

        Map<String, CodeIndex> codeIndexByFile = codeEntries.stream()
                .collect(Collectors.toMap(
                        CodeIndex::getFilePath,
                        e -> e,
                        (a, b) -> {
                            double pa = a.getPagerank() != null ? a.getPagerank() : 0.0;
                            double pb = b.getPagerank() != null ? b.getPagerank() : 0.0;
                            return pa >= pb ? a : b;
                        }));

        // 4. 커밋 수 기준 1/3 분할 (oldest-first로 역순)
        List<CommitHeader> chronological = new ArrayList<>(included);
        Collections.reverse(chronological);

        int n = chronological.size();
        int perBucket  = maxCommits / 3;
        int lateBucket = maxCommits - 2 * perBucket; // 나머지 흡수

        List<CommitHeader> early = chronological.subList(0, n / 3);
        List<CommitHeader> mid   = chronological.subList(n / 3, 2 * n / 3);
        List<CommitHeader> late  = chronological.subList(2 * n / 3, n);

        // 5. 각 구간에서 스코어 상위 + 도메인 다양성 선택
        List<CommitHeader> selected = new ArrayList<>();
        selected.addAll(selectWithDiversity(early, perBucket,  pageRankByFile));
        selected.addAll(selectWithDiversity(mid,   perBucket,  pageRankByFile));
        selected.addAll(selectWithDiversity(late,  lateBucket, pageRankByFile));

        log.info("Selected {} commits after scoring + time distribution + diversity", selected.size());

        // 6. 선택된 커밋만 diff 조회 (§7.7: 10KB 초과 시 CodeIndex 요약 대체)
        return fetchDiffs(repoPath, selected, codeIndexByFile);
    }

    // ─────────────────────────────────────────────────
    // Git 데이터 수집
    // ─────────────────────────────────────────────────

    /**
     * git log에서 커밋 헤더 + numstat을 한 번에 수집한다.
     *
     * <p>포맷: {@code %x00%h%x1f%s%x1f%b%x1f%ai --numstat}
     * <ul>
     *   <li>\x00 = 레코드 구분자</li>
     *   <li>\x1f = 필드 구분자 (sha, subject, body, date, numstat 순)</li>
     *   <li>--numstat: 커밋 포맷 이후 "추가줄\t삭제줄\t파일경로" 행 추가</li>
     * </ul>
     */
    private List<CommitHeader> getAllCommitHeaders(Path repoPath, String authorEmail) {
        List<String> command = List.of(
                "git", "log",
                "--author=" + authorEmail,
                "--no-merges",
                "--format=%x00%h%x1f%s%x1f%b%x1f%ai",
                "--numstat"
        );

        String raw = runGit(command, repoPath);
        List<CommitHeader> headers = new ArrayList<>();

        for (String record : raw.split("\0")) {
            if (record.isBlank()) continue;

            // \x1f 기준 최대 4 필드 분리: sha, subject, body, "date\nnumstat..."
            String[] fields = record.split("\u001f", 4);
            String sha     = fields[0].trim();
            String subject = fields.length > 1 ? fields[1].trim() : "";
            String body    = fields.length > 2 ? fields[2].trim() : "";
            String rest    = fields.length > 3 ? fields[3] : "";

            if (sha.isEmpty() || subject.isEmpty()) continue;

            // rest의 첫 줄 = date, 이후 줄 = numstat 행
            String[] restLines = rest.split("\n");
            String date = restLines[0].trim();

            int additions = 0, deletions = 0;
            List<String> files = new ArrayList<>();
            for (int i = 1; i < restLines.length; i++) {
                String line = restLines[i].trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t", 3);
                if (parts.length == 3) {
                    try {
                        additions += Integer.parseInt(parts[0]);
                        deletions += Integer.parseInt(parts[1]);
                        files.add(parts[2].trim());
                    } catch (NumberFormatException ignored) {
                        // 바이너리 파일 "-\t-\tfile" 형태는 건너뜀
                    }
                }
            }

            headers.add(new CommitHeader(sha, subject, body, date, additions, deletions, files));
        }

        return headers;
    }

    private List<DiffEntry> fetchDiffs(Path repoPath, List<CommitHeader> headers,
                                       Map<String, CodeIndex> codeIndexByFile) {
        List<DiffEntry> result = new ArrayList<>();
        for (CommitHeader h : headers) {
            String diff = getCommitDiff(repoPath, h.sha(), h.files(), codeIndexByFile);
            result.add(new DiffEntry(h.sha(), h.subject(), h.body(), diff));
        }
        return result;
    }

    /**
     * 커밋 diff를 가져온다.
     *
     * <p>§7.7: diff가 {@value #DIFF_COMPRESSION_THRESHOLD}자를 초과하면
     * raw diff 대신 CodeIndex 기반 요약(FQN, 메서드 시그니처, PageRank)으로 대체한다.
     * 대용량 설정 파일·Migration 덤프가 토큰 예산을 소비하는 것을 방지한다.
     */
    private String getCommitDiff(Path repoPath, String sha, List<String> commitFiles,
                                  Map<String, CodeIndex> codeIndexByFile) {
        List<String> command = Arrays.asList("git", "diff", sha + "^", sha);
        try {
            String diff = runGit(command, repoPath);
            if (diff.length() > DIFF_COMPRESSION_THRESHOLD) {
                return buildCodeIndexSummary(commitFiles, diff.length(), codeIndexByFile);
            }
            return diff;
        } catch (Exception e) {
            try {
                return runGit(List.of("git", "show", "--stat", sha), repoPath);
            } catch (Exception ex) {
                log.warn("Failed to get diff for sha={}: {}", sha, ex.getMessage());
                return "";
            }
        }
    }

    /**
     * diff 대신 전달할 CodeIndex 기반 요약을 생성한다.
     *
     * <p>CodeIndex에 없는 파일은 경로만 표시한다.
     */
    private String buildCodeIndexSummary(List<String> commitFiles, int rawDiffSize,
                                          Map<String, CodeIndex> codeIndexByFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("[diff compressed — ").append(rawDiffSize / 1024).append("KB raw, CodeIndex summary]\n");

        for (String file : commitFiles) {
            CodeIndex ci = codeIndexByFile.get(file);
            if (ci != null) {
                sb.append("  ").append(ci.getFqn());
                if (ci.getPagerank() != null) {
                    sb.append("  pagerank=").append(String.format("%.2f", ci.getPagerank()));
                }
                sb.append("\n");
                String methods = ci.getMethods();
                if (methods != null && !methods.isBlank()) {
                    List<String> sigs = extractMethodSignatures(methods);
                    if (!sigs.isEmpty()) {
                        sb.append("    methods: ").append(String.join(", ", sigs)).append("\n");
                    }
                }
            } else {
                sb.append("  ").append(file).append("\n");
            }
        }

        return sb.toString();
    }

    /** CodeIndex.methods JSON에서 signature 필드 값 목록을 추출한다. */
    private List<String> extractMethodSignatures(String methodsJson) {
        List<String> sigs = new ArrayList<>();
        Matcher m = METHOD_SIGNATURE_PATTERN.matcher(methodsJson);
        while (m.find()) {
            sigs.add(m.group(1));
        }
        return sigs;
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

    // ─────────────────────────────────────────────────
    // 스코어링 (§7.4)
    // ─────────────────────────────────────────────────

    private int score(CommitHeader h, Map<String, Double> pageRankByFile) {
        int s = 0;

        // prefix 점수
        if (PREFIX_FEAT.matcher(h.subject()).find())          s += 5;
        else if (PREFIX_FIX.matcher(h.subject()).find())      s += 3;
        else if (PREFIX_REFACTOR.matcher(h.subject()).find()) s += 2;

        // body 존재 (techDecisions 후보 가능성)
        if (!h.body().isBlank()) s += 5;

        // 라인 수 log scale (대용량 파일 과대평가 방지)
        int totalLines = h.additions() + h.deletions();
        if (totalLines > 0) s += (int) (Math.log10(totalLines + 1) * 3);

        // 키워드-라인 역비례 보너스: subject/body에 키워드 + 짧은 diff
        String subjectAndBody = h.subject() + "\n" + h.body();
        if (INTERVIEW_KEYWORD_PATTERN.matcher(subjectAndBody).find()
                && totalLines > 0 && totalLines <= 50) {
            s += 6;
        }

        // 건드린 파일의 최고 PageRank
        if (!h.files().isEmpty()) {
            double maxPr = h.files().stream()
                    .mapToDouble(f -> pageRankByFile.getOrDefault(f, 0.0))
                    .max().orElse(0.0);
            s += (int) (maxPr * 8);

            // 핵심 파일(Service/Controller/Repository/Domain) 비율
            long coreCount = h.files().stream()
                    .filter(f -> CORE_FILE_PATTERN.matcher(f).find())
                    .count();
            s += (int) ((double) coreCount / h.files().size() * 10);
        }

        return s;
    }

    // ─────────────────────────────────────────────────
    // 다양성 선택 (§7.5)
    // ─────────────────────────────────────────────────

    /**
     * 스코어 내림차순으로 정렬 후 도메인 버킷당 최대 {@value #MAX_BUCKET_COMMITS}개 제약으로 선택.
     * 제약으로 maxCount 미달 시 남은 슬롯은 완화 후 채운다.
     */
    private List<CommitHeader> selectWithDiversity(List<CommitHeader> candidates, int maxCount,
                                                   Map<String, Double> pageRankByFile) {
        if (candidates.isEmpty() || maxCount <= 0) return List.of();

        List<CommitHeader> sorted = candidates.stream()
                .sorted(Comparator.comparingInt((CommitHeader h) -> score(h, pageRankByFile)).reversed())
                .toList();

        Map<String, Integer> bucketCount = new HashMap<>();
        List<CommitHeader> selected = new ArrayList<>();
        List<CommitHeader> deferred = new ArrayList<>();

        for (CommitHeader h : sorted) {
            if (selected.size() >= maxCount) break;
            String bucket = getDomainBucket(h.files());
            if (bucketCount.getOrDefault(bucket, 0) < MAX_BUCKET_COMMITS) {
                selected.add(h);
                bucketCount.merge(bucket, 1, Integer::sum);
            } else {
                deferred.add(h);
            }
        }

        // 다양성 제약으로 부족하면 제약 완화 후 채움
        if (selected.size() < maxCount) {
            Set<String> seen = selected.stream()
                    .map(CommitHeader::sha)
                    .collect(Collectors.toSet());
            for (CommitHeader h : deferred) {
                if (selected.size() >= maxCount) break;
                if (seen.add(h.sha())) selected.add(h);
            }
        }

        return selected;
    }

    /**
     * 변경 파일 목록에서 가장 빈번한 도메인 버킷을 반환한다.
     * 버킷 = 파일 경로의 의미 있는 상위 2단계 디렉터리.
     */
    private String getDomainBucket(List<String> files) {
        if (files.isEmpty()) return "unknown";
        return files.stream()
                .map(this::extractDomainBucket)
                .collect(Collectors.groupingBy(b -> b, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    /**
     * 파일 경로에서 도메인 버킷을 추출한다.
     *
     * <p>탐색 순서:
     * <ol>
     *   <li>경로에 domain/config/global/infrastructure/resources 키워드가 있으면
     *       해당 컴포넌트 + 다음 서브디렉터리를 버킷으로 사용</li>
     *   <li>없으면 파일명 바로 위 2단계 디렉터리 사용 (fallback)</li>
     * </ol>
     *
     * <p>예시:
     * <pre>
     * src/main/java/com/back/domain/user/UserService.java  → domain/user
     * src/main/java/com/back/config/SecurityConfig.java    → config
     * src/main/resources/db/migration/V1.sql               → resources/db
     * </pre>
     */
    private String extractDomainBucket(String filePath) {
        String[] parts = filePath.replace("\\", "/").split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            String p = parts[i].toLowerCase();
            if (p.equals("domain") || p.equals("config") || p.equals("global")
                    || p.equals("infrastructure") || p.equals("resources")) {
                if (i + 1 < parts.length - 1) return p + "/" + parts[i + 1];
                return p;
            }
        }
        // fallback: 파일명 위 2단계
        if (parts.length >= 3) return parts[parts.length - 3] + "/" + parts[parts.length - 2];
        if (parts.length >= 2) return parts[parts.length - 2];
        return "root";
    }

    /** git log에서 파싱한 커밋 헤더 + numstat */
    private record CommitHeader(
            String sha,
            String subject,
            String body,
            String date,        // ISO 8601, 예: "2025-01-01 10:00:00 +0900"
            int additions,
            int deletions,
            List<String> files  // 변경된 파일 경로 목록
    ) {}
}
