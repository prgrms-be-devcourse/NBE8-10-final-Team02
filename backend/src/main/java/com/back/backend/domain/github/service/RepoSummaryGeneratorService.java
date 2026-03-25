package com.back.backend.domain.github.service;

import com.back.backend.domain.ai.client.AiClient;
import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.client.AiRequest;
import com.back.backend.domain.ai.client.AiResponse;
import com.back.backend.domain.ai.template.PromptTemplate;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.github.entity.CodeIndex;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.RepoSummary;
import com.back.backend.domain.github.repository.RepoSummaryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Repo 단위 AI 포트폴리오 요약 생성 서비스 (설계 §3, §6 ⑥).
 *
 * 흐름:
 *   1. CodeIndexService에서 code structure 조회
 *   2. ContributionExtractorService에서 diff 조회
 *   3. PortfolioPromptBuilder로 userMessage 구성
 *   4. AiClient 호출 (ai.portfolio.summary.v1 템플릿)
 *   5. 응답 검증 후 repo_summaries 저장
 *
 * 출력 형식: portfolio-summary.schema.json (projects[] 1개 항목)
 * 저장: repo_summaries.data = AI 응답 JSON
 */
@Service
public class RepoSummaryGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(RepoSummaryGeneratorService.class);
    private static final String TEMPLATE_ID = "ai.portfolio.summary.v1";

    private final AiClientRouter aiClientRouter;
    private final PromptTemplateRegistry templateRegistry;
    private final PortfolioPromptBuilder promptBuilder;
    private final CodeIndexService codeIndexService;
    private final ContributionExtractorService contributionExtractorService;
    private final RepoCloneService repoCloneService;
    private final RepoSummaryRepository repoSummaryRepository;

    public RepoSummaryGeneratorService(
            AiClientRouter aiClientRouter,
            PromptTemplateRegistry templateRegistry,
            PortfolioPromptBuilder promptBuilder,
            CodeIndexService codeIndexService,
            ContributionExtractorService contributionExtractorService,
            RepoCloneService repoCloneService,
            RepoSummaryRepository repoSummaryRepository
    ) {
        this.aiClientRouter = aiClientRouter;
        this.templateRegistry = templateRegistry;
        this.promptBuilder = promptBuilder;
        this.codeIndexService = codeIndexService;
        this.contributionExtractorService = contributionExtractorService;
        this.repoCloneService = repoCloneService;
        this.repoSummaryRepository = repoSummaryRepository;
    }

    /**
     * 특정 repo의 AI 요약을 생성하고 저장한다.
     *
     * @param user          요청 사용자
     * @param repo          대상 repo
     * @param authorEmail   본인 GitHub primary email (git log --author용)
     * @param triggerReason new_repo | significant_commits | manual
     * @return 저장된 RepoSummary 엔티티
     */
    @Transactional
    public RepoSummary generate(User user, GithubRepository repo,
                                String authorEmail, String triggerReason) {
        log.info("Generating repo summary: userId={}, repoId={}, trigger={}",
                user.getId(), repo.getId(), triggerReason);

        // 1. Code Index 조회 (PageRank 필터링은 PortfolioPromptBuilder 내부에서)
        List<CodeIndex> codeEntries = codeIndexService.getTopByPageRank(repo, 0.0);

        // 2. 기여 diff 조회 (최근 30개, 외부 repo는 20개)
        boolean isOwnedRepo = isOwnedRepo(repo, user);
        int maxDiffs = isOwnedRepo ? 30 : 20;
        List<DiffEntry> diffs = contributionExtractorService.getContributionDiffs(
                repoCloneService.getRepoPath(user.getId(), repo.getId()),
                authorEmail,
                maxDiffs
        );

        // 3. README/docs 읽기 (large repo에서 전체 코드 구조 파악 보완용, 항상 시도)
        Path repoPath = repoCloneService.getRepoPath(user.getId(), repo.getId());
        String projectOverview = readProjectOverview(repoPath);

        // 4. userMessage 구성
        String userMessage = promptBuilder.buildUserMessage(
                repo, authorEmail, codeEntries, diffs, isOwnedRepo, projectOverview);

        // 5. AI 호출
        String summaryJson = callAi(userMessage);

        // 6. 버전 계산 및 저장
        int nextVersion = getNextVersion(user, repo);
        RepoSummary summary = RepoSummary.builder()
                .user(user)
                .githubRepository(repo)
                .summaryVersion(nextVersion)
                .data(summaryJson)
                .triggerReason(triggerReason)
                .significanceScore(null)
                .generatedAt(Instant.now())
                .build();

        RepoSummary saved = repoSummaryRepository.save(summary);
        log.info("Repo summary saved: repoId={}, version={}, tokenUsage=unknown",
                repo.getId(), nextVersion);
        return saved;
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    private String callAi(String userMessage) {
        PromptTemplate template = templateRegistry.get(TEMPLATE_ID);
        AiClient client = aiClientRouter.getDefault();

        AiRequest request = new AiRequest(
                promptBuilder.loadSystemPrompt(),
                promptBuilder.loadDeveloperPrompt(),
                userMessage,
                template.temperature(),
                template.maxTokens()
        );

        int attempts = 0;
        int maxRetries = template.retryPolicy().maxRetries();
        Exception lastException = null;

        while (attempts <= maxRetries) {
            try {
                AiResponse response = client.call(request);
                String content = response.content();
                if (content == null || content.isBlank()) {
                    throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR,
                            HttpStatus.INTERNAL_SERVER_ERROR, "AI 응답이 비어있습니다.");
                }
                return content;
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                attempts++;
                log.warn("AI call attempt {} failed: {}", attempts, e.getMessage());
            }
        }

        throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "AI 호출 실패: " + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * 클론된 repo에서 README 및 프로젝트 구조 문서를 읽는다.
     *
     * 탐색 순서:
     *   1. README.md / readme.md / README.MD
     *   2. README.rst / README.txt (없으면 생략)
     *
     * 최대 8,000자로 잘라낸다. 없으면 null 반환.
     *
     * TODO: 읽는 문서 목록과 최대 글자 수(8,000)는 실제 AI 품질을 보며 조정 필요.
     *   - CONTRIBUTING.md, docs/README.md 등 추가 후보 검토
     *   - large repo에서 README만으로 부족할 경우 디렉터리 tree 출력 추가 검토
     */
    private String readProjectOverview(Path repoPath) {
        if (repoPath == null || !Files.exists(repoPath)) return null;

        // TODO: 후보 파일 목록은 실무 피드백에 따라 확장 (예: CONTRIBUTING.md, docs/index.md)
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
                    log.info("Project overview loaded from: {}", name);
                    return content;
                } catch (IOException e) {
                    log.warn("Failed to read {}: {}", file, e.getMessage());
                }
            }
        }
        return null;
    }

    private boolean isOwnedRepo(GithubRepository repo, User user) {
        return user.getId().equals(
                repo.getGithubConnection().getUser().getId()
        ) && repo.getOwnerLogin().equals(repo.getGithubConnection().getGithubLogin());
    }

    private int getNextVersion(User user, GithubRepository repo) {
        return repoSummaryRepository
                .findTopByUserAndGithubRepositoryOrderBySummaryVersionDesc(user, repo)
                .map(s -> s.getSummaryVersion() + 1)
                .orElse(1);
    }
}
