package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * clone → 정적 분석 → Code Index → RepoSummary → MergedSummary 파이프라인 오케스트레이터.
 *
 * 흐름:
 *   triggerAnalysis() — HTTP 요청 스레드 (동기)
 *     → setPending
 *     → executeAsync()  ← @Async("analysisExecutor")
 *         1. significance_check  (SKIPPED이면 종료)
 *         2. clone
 *         3. analysis (정적 분석 + PageRank + Code Index 저장)
 *         4. summary  (RepoSummary AI 생성 + MergedSummary 재집계)
 *         → setCompleted / setSkipped / setFailed
 *
 * 주의:
 *   - @Async 메서드는 같은 빈 내부에서 직접 호출하면 프록시를 우회한다.
 *     triggerAnalysis → executeAsync 호출은 Spring 프록시를 통해야 하므로
 *     self-injection 패턴을 사용한다.
 *   - 트랜잭션이 필요한 DB 작업은 각 서비스(CodeIndexService 등)에 위임한다.
 *     파이프라인 자체는 트랜잭션을 열지 않는다.
 */
@Service
public class AnalysisPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipelineService.class);

    // 예상 소요 시간 (임시 추정치)
    private static final int ESTIMATED_MINUTES = 5;

    // TODO: large repo 임계값은 실제 분석 결과를 보며 조정 필요.
    //   현재 300은 초기 추정치. 분석 시간·AI 품질을 모니터링하여 튜닝한다.
    private static final int LARGE_REPO_FILE_THRESHOLD = 300;

    private final UserRepository userRepository;
    private final GithubRepositoryRepository repositoryRepository;
    private final GithubCommitRepository commitRepository;
    private final SyncStatusService syncStatusService;
    private final SignificanceCheckService significanceCheckService;
    private final RepoCloneService repoCloneService;
    private final StaticAnalysisService staticAnalysisService;
    private final CallGraphService callGraphService;
    private final CodeIndexService codeIndexService;
    private final ContributionExtractorService contributionExtractorService;
    private final RepoSummaryGeneratorService repoSummaryGeneratorService;
    private final MergedSummaryService mergedSummaryService;

    // Self-injection: @Async 프록시를 통해 executeAsync를 호출하기 위함
    private AnalysisPipelineService self;

    public AnalysisPipelineService(
            UserRepository userRepository,
            GithubRepositoryRepository repositoryRepository,
            GithubCommitRepository commitRepository,
            SyncStatusService syncStatusService,
            SignificanceCheckService significanceCheckService,
            RepoCloneService repoCloneService,
            StaticAnalysisService staticAnalysisService,
            CallGraphService callGraphService,
            CodeIndexService codeIndexService,
            ContributionExtractorService contributionExtractorService,
            RepoSummaryGeneratorService repoSummaryGeneratorService,
            MergedSummaryService mergedSummaryService
    ) {
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.syncStatusService = syncStatusService;
        this.significanceCheckService = significanceCheckService;
        this.repoCloneService = repoCloneService;
        this.staticAnalysisService = staticAnalysisService;
        this.callGraphService = callGraphService;
        this.codeIndexService = codeIndexService;
        this.contributionExtractorService = contributionExtractorService;
        this.repoSummaryGeneratorService = repoSummaryGeneratorService;
        this.mergedSummaryService = mergedSummaryService;
    }

    // Spring이 주입한 자기 자신 (프록시) — @Lazy로 순환 참조 방지
    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@Lazy AnalysisPipelineService self) {
        this.self = self;
    }

    // ─────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────

    /**
     * 분석 파이프라인을 비동기로 시작한다. HTTP 요청 스레드에서 즉시 반환한다.
     *
     * @param userId       인증된 사용자 ID
     * @param repositoryId 분석 대상 repo ID
     * @throws ServiceException repo가 없거나 접근 불가인 경우
     */
    @Transactional(readOnly = true)
    public void triggerAnalysis(Long userId, Long repositoryId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.AUTH_REQUIRED,
                        HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        GithubRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ServiceException(ErrorCode.GITHUB_REPOSITORY_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "저장소를 찾을 수 없습니다."));

        // 다른 사용자의 repo에 접근하지 못하도록 검증
        if (!repo.getGithubConnection().getUser().getId().equals(userId)) {
            throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                    HttpStatus.FORBIDDEN, "접근 권한이 없는 저장소입니다.");
        }

        // PENDING 상태 등록 (estimatedEndAt = 현재 + ESTIMATED_MINUTES)
        Instant estimatedEnd = Instant.now().plusSeconds(ESTIMATED_MINUTES * 60L);
        syncStatusService.setPending(userId, repositoryId, estimatedEnd);

        log.info("Analysis pipeline triggered: userId={}, repoId={}", userId, repositoryId);

        // @Async 프록시를 통해 비동기 실행
        self.executeAsync(userId, repositoryId);
    }

    // ─────────────────────────────────────────────────
    // Async pipeline
    // ─────────────────────────────────────────────────

    /**
     * 파이프라인 전 단계를 비동기로 실행한다.
     * 반드시 @Async 프록시를 통해 호출되어야 한다 (self.executeAsync()로 호출할 것).
     */
    @Async("analysisExecutor")
    public void executeAsync(Long userId, Long repositoryId) {
        // 트랜잭션 외부에서 엔티티를 다시 로드 (영속성 컨텍스트 분리)
        User user = userRepository.findById(userId).orElse(null);
        GithubRepository repo = repositoryRepository.findById(repositoryId).orElse(null);

        if (user == null || repo == null) {
            log.error("Pipeline aborted: user or repo not found. userId={}, repoId={}", userId, repositoryId);
            syncStatusService.setFailed(userId, repositoryId, "사용자 또는 저장소를 찾을 수 없습니다.");
            return;
        }

        try {
            runPipeline(user, repo, userId, repositoryId);
        } catch (Exception e) {
            log.error("Pipeline failed: userId={}, repoId={}, error={}", userId, repositoryId, e.getMessage(), e);
            syncStatusService.setFailed(userId, repositoryId, summarizeError(e));
        } finally {
            repoCloneService.deleteRepo(userId, repositoryId);
        }
    }

    // ─────────────────────────────────────────────────
    // 파이프라인 단계
    // ─────────────────────────────────────────────────

    private void runPipeline(User user, GithubRepository repo, Long userId, Long repositoryId) {

        // ① Significance Check
        syncStatusService.setInProgress(userId, repositoryId, "significance_check");
        boolean significant = significanceCheckService.isSignificant(repo, userId);
        if (!significant) {
            log.info("Pipeline skipped (not significant): userId={}, repoId={}", userId, repositoryId);
            syncStatusService.setSkipped(userId, repositoryId);
            significanceCheckService.markAnalyzed(userId, repositoryId);
            return;
        }

        // ② Clone / Fetch
        syncStatusService.setInProgress(userId, repositoryId, "clone");
        Path repoPath = repoCloneService.cloneOrFetch(repo.getHtmlUrl(), userId, repositoryId);

        // ③ 정적 분석 + PageRank + Code Index 저장
        syncStatusService.setInProgress(userId, repositoryId, "analysis");

        String authorEmail = resolveAuthorEmail(repo, user);
        Set<String> authoredFiles = contributionExtractorService.getAuthoredFiles(repoPath, authorEmail);

        // large repo이면 본인 기여 파일만 정적 분석 (전체 분석은 비용 대비 효과 낮음)
        // 부족한 전체 구조 파악은 README/docs로 보완 (RepoSummaryGeneratorService에서 프롬프트에 주입)
        boolean largeRepo = isLargeRepo(repoPath);
        Optional<Set<String>> analyzeScope = largeRepo ? Optional.of(authoredFiles) : Optional.empty();
        if (largeRepo) {
            log.info("Large repo detected (>{} source files), analyzing authored files only: userId={}, repoId={}",
                    LARGE_REPO_FILE_THRESHOLD, userId, repositoryId);
        }

        List<AnalysisNode> nodes = staticAnalysisService.analyze(repoPath, analyzeScope);
        // 노드들 간의 호출 관계(call graph) 를 바탕으로 PageRank 점수를 계산한다. 많이 호출되는 핵심 함수일수록
        //  점수가 높아진다. 나중에 AI 요약할 때 어떤 코드가 중요한지 판단하는 기준이 된다.
        Map<String, Double> pagerankMap = callGraphService.computePageRank(nodes);

        // triggerReason은 buildIndex 전에 결정 (기존 index 유무로 판단)
        String triggerReason = determineTriggerReason(repo);
        // 분석 결과(nodes, 기여 파일, PageRank 점수)를 DB의 code_index 테이블에 저장한다. AI가 나중에 여기서 읽는다.
        codeIndexService.buildIndex(repo, userId, nodes, authoredFiles, pagerankMap);

        // ④ RepoSummary AI 생성 + MergedSummary 재집계
        syncStatusService.setInProgress(userId, repositoryId, "summary");

        // AI를 호출
        repoSummaryGeneratorService.generate(user, repo, authorEmail, triggerReason);
        mergedSummaryService.rebuild(user);

        // 완료
        syncStatusService.setCompleted(userId, repositoryId);
        significanceCheckService.markAnalyzed(userId, repositoryId);
        log.info("Pipeline completed: userId={}, repoId={}", userId, repositoryId);
    }

    // ─────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────

    /**
     * git log --author 에 사용할 이메일을 결정한다.
     *
     * github_commits 테이블의 author_email(is_user_commit=true)을 우선 사용.
     * 없으면 github_login으로 폴백 (public repo, email 비공개 케이스).
     */
    private String resolveAuthorEmail(GithubRepository repo, User user) {
        List<String> emails = commitRepository
                .findDistinctAuthorEmailsByRepositoryAndUserCommitTrue(repo);
        if (!emails.isEmpty()) {
            return emails.get(0);
        }
        // 폴백: GitHub 로그인 ID (noreply 이메일 형식이 아닌 경우도 git log에서 작동함)
        String githubLogin = repo.getGithubConnection().getGithubLogin();
        log.warn("No author_email found for repoId={}, falling back to githubLogin={}", repo.getId(), githubLogin);
        return githubLogin;
    }

    /**
     * RepoSummary 생성 트리거 이유를 결정한다.
     * 이전 요약이 없으면 new_repo, 있으면 significant_commits.
     */
    private String determineTriggerReason(GithubRepository repo) {
        // buildIndex 호출 전에 판단: 기존 code_index가 있으면 재분석, 없으면 최초 분석
        boolean hasIndex = !codeIndexService.getAuthoredEntries(repo).isEmpty();
        return hasIndex ? "significant_commits" : "new_repo";
    }

    /**
     * repo의 소스 파일 수가 LARGE_REPO_FILE_THRESHOLD를 초과하면 true.
     * 집계 실패 시 false 반환 (안전하게 전체 분석 시도).
     */
    private boolean isLargeRepo(Path repoPath) {
        try (var stream = Files.walk(repoPath, 8)) {
            long count = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".kts")
                                || name.endsWith(".py") || name.endsWith(".ts") || name.endsWith(".tsx")
                                || name.endsWith(".js") || name.endsWith(".jsx")
                                || name.endsWith(".go") || name.endsWith(".rs")
                                || name.endsWith(".cpp") || name.endsWith(".c");
                    })
                    .count();
            return count > LARGE_REPO_FILE_THRESHOLD;
        } catch (IOException e) {
            log.warn("Failed to count source files in repo: {}, defaulting to small repo", repoPath);
            return false;
        }
    }

    private String summarizeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }
}
