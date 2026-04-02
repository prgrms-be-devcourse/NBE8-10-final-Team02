package com.back.backend.domain.github.portfolio;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.github.analysis.CodeIndexService;
import com.back.backend.domain.github.analysis.ContributionExtractorService;
import com.back.backend.domain.github.analysis.DiffEntry;
import com.back.backend.domain.github.analysis.RepoCloneService;
import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.RepoSummary;
import com.back.backend.domain.github.repository.RepoSummaryRepository;
import com.back.backend.domain.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 여러 repo 데이터를 단 1회의 AI 호출로 처리하는 배치 요약 생성 서비스.
 *
 * <h3>설계 배경</h3>
 * 기존 {@link RepoSummaryGeneratorService}는 repo마다 AI를 1회씩 호출했다.
 * Gemini 무료·Groq 무료 환경에서는 분당 호출 횟수(Rate Limit)가 매우 빡빡하므로,
 * 선택된 모든 repo 데이터를 하나의 XML 페이로드로 묶어 단 1회 AI 호출로 처리한다.
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>각 repo에 대해 CodeIndex + diff + README 수집</li>
 *   <li>diff를 Early/Mid/Late 3구간으로 분할 (시간 분포 반영)</li>
 *   <li>{@link BatchTokenBudget}으로 2D Rollover 예산 계산</li>
 *   <li>{@link BatchPortfolioPromptBuilder}로 {@code <batch_data>} XML 조립</li>
 *   <li>{@link AiPipeline}으로 {@code ai.portfolio.summary.batch.v1} 템플릿 단 1회 호출</li>
 *   <li>응답 JSON 배열을 repoId 기준으로 파싱 → repo별 {@link RepoSummary} 저장</li>
 * </ol>
 *
 * <h3>실패 처리</h3>
 * <ul>
 *   <li>배치 AI 호출 전체 실패: 예외를 호출자({@link com.back.backend.domain.github.analysis.AnalysisPipelineService})로 전파</li>
 *   <li>개별 repo 응답 매핑 실패: 해당 repo는 skip + 경고 로그 (다른 repo는 정상 저장)</li>
 * </ul>
 */
@Service
public class BatchRepoSummaryGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(BatchRepoSummaryGeneratorService.class);
    private static final String BATCH_TEMPLATE_ID = "ai.portfolio.summary.batch.v1";

    // 소유 repo / 외부 repo 최대 커밋 수 (기존 RepoSummaryGeneratorService와 동일)
    private static final int OWNED_MAX_COMMITS    = 30;
    private static final int EXTERNAL_MAX_COMMITS = 20;

    private final AiPipeline aiPipeline;
    private final BatchPortfolioPromptBuilder promptBuilder;
    private final CodeIndexService codeIndexService;
    private final ContributionExtractorService contributionExtractorService;
    private final RepoCloneService repoCloneService;
    private final RepoSummaryRepository repoSummaryRepository;
    private final BatchTokenBudgetProperties budgetProperties;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public BatchRepoSummaryGeneratorService(
            AiPipeline aiPipeline,
            BatchPortfolioPromptBuilder promptBuilder,
            CodeIndexService codeIndexService,
            ContributionExtractorService contributionExtractorService,
            RepoCloneService repoCloneService,
            RepoSummaryRepository repoSummaryRepository,
            BatchTokenBudgetProperties budgetProperties,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        this.aiPipeline = aiPipeline;
        this.promptBuilder = promptBuilder;
        this.codeIndexService = codeIndexService;
        this.contributionExtractorService = contributionExtractorService;
        this.repoCloneService = repoCloneService;
        this.repoSummaryRepository = repoSummaryRepository;
        this.budgetProperties = budgetProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 여러 repo를 하나의 배치로 AI 요약을 생성하고 각 repo의 RepoSummary를 저장한다.
     *
     * @param user        요청 사용자
     * @param repos       분석 대상 repo 목록 (모든 repo의 static analysis가 완료된 상태)
     * @param authorEmail git log --author 에 사용할 본인 이메일
     * @param triggerReason new_repo | significant_commits | manual
     * @return 저장된 RepoSummary 목록 (일부 repo 매핑 실패 시 해당 repo는 제외됨)
     */
    public List<RepoSummary> generateAll(User user, List<GithubRepository> repos,
                                          String authorEmail, String triggerReason) {
        if (repos.isEmpty()) {
            log.warn("generateAll called with empty repo list for userId={}", user.getId());
            return List.of();
        }

        log.info("Batch summary generation started: userId={}, repoCount={}, trigger={}",
                user.getId(), repos.size(), triggerReason);

        // ── Step 1: 각 repo 데이터 수집 ────────────────────────────────
        List<BatchPortfolioPromptBuilder.RepoBatchData> repoBatchDataList =
                collectAllRepoData(user, repos, authorEmail);

        if (repoBatchDataList.isEmpty()) {
            log.warn("No repo data could be collected for batch. userId={}", user.getId());
            return List.of();
        }

        // ── Step 2: 2D Rollover 예산 생성 ──────────────────────────────
        // 배치 호출 1회에 대한 전체 예산을 repo 수로 나눠 관리
        BatchTokenBudget budget = new BatchTokenBudget(
                budgetProperties.getGlobalBudgetChars(), repoBatchDataList.size());

        // ── Step 3: XML 배치 페이로드 조립 ────────────────────────────
        String batchPayload = promptBuilder.build(repoBatchDataList, budget);
        log.info("Batch payload built: chars={}, repoCount={}",
                batchPayload.length(), repoBatchDataList.size());

        // ── Step 4: 단 1회 AI 호출 ────────────────────────────────────
        // ai.portfolio.summary.batch.v1 템플릿 → JSON 배열 응답
        JsonNode responseArray = aiPipeline.execute(BATCH_TEMPLATE_ID, batchPayload);
        log.info("Batch AI call completed. responseSize={}", responseArray.size());

        // ── Step 5: 응답 배열 파싱 → repoId 기준으로 repo 매핑 ─────────
        // repoKey → GithubRepository 역매핑 테이블 구성
        Map<String, GithubRepository> repoByKey = buildRepoKeyMap(repoBatchDataList);

        // ── Step 6: 각 repo의 RepoSummary 저장 ─────────────────────────
        return saveAllSummaries(user, responseArray, repoByKey, triggerReason);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 데이터 수집
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 각 repo에 대해 CodeIndex, diff(3구간 분할), README를 수집하여
     * {@link BatchPortfolioPromptBuilder.RepoBatchData} 목록을 반환한다.
     *
     * <p>단일 repo 수집 실패 시 해당 repo를 skip하고 경고 로그를 남긴다.
     */
    private List<BatchPortfolioPromptBuilder.RepoBatchData> collectAllRepoData(
            User user, List<GithubRepository> repos, String authorEmail) {

        List<BatchPortfolioPromptBuilder.RepoBatchData> result = new ArrayList<>();

        for (GithubRepository repo : repos) {
            try {
                BatchPortfolioPromptBuilder.RepoBatchData data =
                        collectSingleRepoData(user, repo, authorEmail);
                result.add(data);
            } catch (Exception e) {
                log.warn("Failed to collect data for repoId={}, skipping. reason={}",
                        repo.getId(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * 단일 repo의 데이터를 수집한다.
     *
     * <ol>
     *   <li>CodeIndex 조회 (PageRank + authoredByMe)</li>
     *   <li>IGNORED 제거된 diff 조회 → Early/Mid/Late 3구간으로 분할</li>
     *   <li>README 읽기</li>
     * </ol>
     */
    private BatchPortfolioPromptBuilder.RepoBatchData collectSingleRepoData(
            User user, GithubRepository repo, String authorEmail) {

        // 1. CodeIndex 조회
        List<CodeIndex> codeEntries = codeIndexService.getTopByPageRank(repo, 0.0);

        // 2. 소유 여부에 따라 최대 커밋 수 결정
        boolean isOwnedRepo = isOwnedRepo(repo, user);
        int maxCommits = isOwnedRepo ? OWNED_MAX_COMMITS : EXTERNAL_MAX_COMMITS;

        // 3. diff 수집 (ContributionExtractorService가 이미 Early→Mid→Late 순으로 반환)
        Path repoPath = repoCloneService.getRepoPath(user.getId(), repo.getId());
        List<DiffEntry> allDiffs = contributionExtractorService.getFilteredDiffs(
                repoPath, authorEmail, maxCommits, codeEntries);

        // 4. diff를 Early/Mid/Late 3구간으로 분할
        //    ContributionExtractorService.getFilteredDiffs()는 already Early→Mid→Late 순으로 반환하므로
        //    인덱스 기준 1/3씩 나눈다.
        int total = allDiffs.size();
        List<DiffEntry> earlyDiffs = allDiffs.subList(0, total / 3);
        List<DiffEntry> midDiffs   = allDiffs.subList(total / 3, 2 * total / 3);
        List<DiffEntry> lateDiffs  = allDiffs.subList(2 * total / 3, total);

        // 5. README 읽기
        String projectOverview = readProjectOverview(repoPath);

        log.debug("Collected repo data: repoId={}, codeEntries={}, diffs={}(e={},m={},l={})",
                repo.getId(), codeEntries.size(), total,
                earlyDiffs.size(), midDiffs.size(), lateDiffs.size());

        return new BatchPortfolioPromptBuilder.RepoBatchData(
                repo, isOwnedRepo, projectOverview,
                codeEntries, earlyDiffs, midDiffs, lateDiffs);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 응답 파싱 및 저장
    // ─────────────────────────────────────────────────────────────────────

    /**
     * AI 응답 JSON 배열을 파싱하여 repo별 RepoSummary를 저장한다.
     *
     * <p>각 배열 원소의 {@code repoId}를 {@code repoByKey} 맵에서 찾아
     * 해당 repo의 RepoSummary를 생성한다.
     * 매핑 실패 또는 파싱 오류 발생 시 해당 원소는 skip하고 나머지를 계속 처리한다.
     */
    private List<RepoSummary> saveAllSummaries(
            User user,
            JsonNode responseArray,
            Map<String, GithubRepository> repoByKey,
            String triggerReason) {

        List<RepoSummary> saved = new ArrayList<>();

        for (int i = 0; i < responseArray.size(); i++) {
            JsonNode element = responseArray.get(i);

            // repoId 추출
            JsonNode repoIdNode = element.get("repoId");
            if (repoIdNode == null || repoIdNode.asText().isBlank()) {
                log.warn("Batch response element[{}] missing repoId, skipping.", i);
                continue;
            }
            String repoId = repoIdNode.asText();

            // 해당 repo 매핑 확인
            GithubRepository repo = repoByKey.get(repoId);
            if (repo == null) {
                log.warn("Batch response repoId='{}' not found in request repos, skipping.", repoId);
                continue;
            }

            // project 필드 추출 후 { "project": {...} } 형태로 wrapping
            // (기존 RepoSummary.data 스키마가 단일 {"project": {...}} 형태이므로 호환 유지)
            JsonNode projectNode = element.get("project");
            if (projectNode == null) {
                log.warn("Batch response repoId='{}' missing project field, skipping.", repoId);
                continue;
            }

            try {
                // {"project": {...}} 형태로 직렬화하여 기존 RepoSummary.data 스키마와 호환
                String summaryJson = objectMapper.writeValueAsString(
                        objectMapper.createObjectNode().set("project", projectNode));

                // 트랜잭션 내에서 버전 계산 + 저장
                RepoSummary repoSummary = saveSingleSummary(user, repo, summaryJson, triggerReason);
                if (repoSummary != null) {
                    saved.add(repoSummary);
                    log.info("RepoSummary saved: repoId={}, version={}",
                            repo.getId(), repoSummary.getSummaryVersion());
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize project JSON for repoId='{}': {}", repoId, e.getMessage());
            }
        }

        log.info("Batch summary save completed: userId={}, saved={}/{}",
                user.getId(), saved.size(), responseArray.size());
        return saved;
    }

    /**
     * 단일 RepoSummary를 트랜잭션 내에서 버전 계산 후 저장한다.
     */
    private RepoSummary saveSingleSummary(User user, GithubRepository repo,
                                           String summaryJson, String triggerReason) {
        return transactionTemplate.execute(status -> {
            int nextVersion = repoSummaryRepository
                    .findTopByUserAndGithubRepositoryOrderBySummaryVersionDesc(user, repo)
                    .map(s -> s.getSummaryVersion() + 1)
                    .orElse(1);

            RepoSummary summary = RepoSummary.builder()
                    .user(user)
                    .githubRepository(repo)
                    .summaryVersion(nextVersion)
                    .data(summaryJson)
                    .triggerReason(triggerReason)
                    .significanceScore(null)
                    .generatedAt(Instant.now())
                    .build();

            return repoSummaryRepository.save(summary);
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    /**
     * repoBatchDataList에서 repoKey → GithubRepository 역매핑 테이블을 구성한다.
     *
     * <p>repoKey는 {@link BatchPortfolioPromptBuilder}가 id 속성에 사용하는 값과 동일한 규칙으로 생성.
     * (repoName 소문자 + 하이픈 치환)
     */
    private Map<String, GithubRepository> buildRepoKeyMap(
            List<BatchPortfolioPromptBuilder.RepoBatchData> repoBatchDataList) {
        Map<String, GithubRepository> map = new HashMap<>();
        for (BatchPortfolioPromptBuilder.RepoBatchData data : repoBatchDataList) {
            // BatchPortfolioPromptBuilder.deriveRepoKey()와 동일한 로직으로 key 생성
            String key = data.repo().getRepoName().toLowerCase().replaceAll("[^a-z0-9\\-]", "-");
            map.put(key, data.repo());
        }
        return map;
    }

    /**
     * 클론된 repo에서 README를 읽는다. 최대 8,000자. 없으면 null.
     */
    private String readProjectOverview(Path repoPath) {
        if (repoPath == null || !Files.exists(repoPath)) return null;

        String[] candidates = {
            "README.md", "readme.md", "README.MD",
            "README.rst", "README.txt", "readme.txt"
        };
        for (String name : candidates) {
            Path file = repoPath.resolve(name);
            if (Files.exists(file)) {
                try {
                    String content = Files.readString(file);
                    if (content.length() > 8_000) {
                        content = content.substring(0, 8_000) + "\n... [truncated]";
                    }
                    return content;
                } catch (IOException e) {
                    log.warn("Failed to read {}: {}", file, e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * repo가 본인 소유인지 확인한다.
     * ownerLogin이 GitHub 연결의 githubLogin과 일치하면 본인 소유.
     */
    private boolean isOwnedRepo(GithubRepository repo, User user) {
        return user.getId().equals(repo.getGithubConnection().getUser().getId())
                && repo.getOwnerLogin().equals(repo.getGithubConnection().getGithubLogin());
    }
}
