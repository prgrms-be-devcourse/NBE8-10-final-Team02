package com.back.backend.domain.github.analysis;

import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.portfolio.BatchRepoSummaryGeneratorService;
import com.back.backend.domain.github.portfolio.MergedSummaryService;
import com.back.backend.domain.github.portfolio.RepoSummaryGeneratorService;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.portfolio.service.FailedJobRedisStore;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.security.GitleaksService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

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

    // 예상 소요 시간 구성 요소
    // 정적 분석은 병렬 실행이라 repo 수와 무관하게 고정
    private static final int ESTIMATED_STATIC_MINUTES = 3;
    // AI 배치 호출 1청크당 예상 소요 시간 (Vertex AI 기준)
    private static final int ESTIMATED_AI_MINUTES_PER_CHUNK = 2;
    // Vertex AI 기본 청크 크기 (BatchProviderStrategy.getMaxReposPerCall()과 맞춰야 함)
    private static final int DEFAULT_CHUNK_SIZE = 3;

    // 실행 중인 파이프라인 스레드 추적 (userId:repoId → Thread)
    private final ConcurrentHashMap<String, Thread> activeThreads = new ConcurrentHashMap<>();

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
    private final BatchRepoSummaryGeneratorService batchRepoSummaryGeneratorService;
    private final MergedSummaryService mergedSummaryService;
    private final Executor parallelAnalysisExecutor;
    private final RepoFileFilterService repoFileFilterService;
    private final GitleaksService gitleaksService;
    private final ObjectMapper objectMapper;
    private final FailedJobRedisStore failedJobRedisStore;

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
            BatchRepoSummaryGeneratorService batchRepoSummaryGeneratorService,
            MergedSummaryService mergedSummaryService,
            @org.springframework.beans.factory.annotation.Qualifier("parallelAnalysisExecutor")
            Executor parallelAnalysisExecutor,
            RepoFileFilterService repoFileFilterService,
            GitleaksService gitleaksService,
            ObjectMapper objectMapper,
            FailedJobRedisStore failedJobRedisStore
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
        this.batchRepoSummaryGeneratorService = batchRepoSummaryGeneratorService;
        this.mergedSummaryService = mergedSummaryService;
        this.parallelAnalysisExecutor = parallelAnalysisExecutor;
        this.repoFileFilterService = repoFileFilterService;
        this.gitleaksService = gitleaksService;
        this.objectMapper = objectMapper;
        this.failedJobRedisStore = failedJobRedisStore;
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

        // 이미 PENDING/IN_PROGRESS 상태이면 중복 실행 방지 (git lock 충돌 예방)
        syncStatusService.getStatus(userId, repositoryId).ifPresent(existing -> {
            if (existing.status() == SyncStatus.PENDING || existing.status() == SyncStatus.IN_PROGRESS) {
                log.info("Analysis already in progress, ignoring duplicate request: userId={}, repoId={}, status={}",
                        userId, repositoryId, existing.status());
                throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                        HttpStatus.CONFLICT, "이미 분석이 진행 중입니다. 완료 후 다시 시도해주세요.");
            }
        });

        // PENDING 상태 등록 (단일 repo: 정적 분석 + AI 1청크)
        Instant estimatedEnd = Instant.now().plusSeconds(
                (ESTIMATED_STATIC_MINUTES + ESTIMATED_AI_MINUTES_PER_CHUNK) * 60L);
        syncStatusService.setPending(userId, repositoryId, estimatedEnd);

        log.info("Analysis pipeline triggered: userId={}, repoId={}", userId, repositoryId);

        // @Async 프록시를 통해 비동기 실행
        self.executeAsync(userId, repositoryId);
    }

    /**
     * 배치 분석 파이프라인 전 단계를 비동기로 실행한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>각 repo에 대해 순차적으로 ①②③ (significance check, clone, static analysis) 실행</li>
     *   <li>모든 repo 분석 완료 후 {@link BatchRepoSummaryGeneratorService#generateAll} 단 1회 호출</li>
     *   <li>{@link MergedSummaryService#rebuild}로 MergedSummary 재집계</li>
     * </ol>
     *
     * <p>개별 repo ①②③ 실패 시: 해당 repo를 {@code analyzedRepos} 목록에서 제외하고 나머지 계속.
     * 배치 AI 호출 실패 시: 성공적으로 분석된 모든 repo를 FAILED 처리.
     *
     * <p>반드시 @Async 프록시를 통해 호출되어야 한다 (self.executeBatchAsync()로 호출할 것).
     */
    @Async("analysisExecutor")
    public void executeBatchAsync(Long userId, List<Long> repositoryIds) {
        log.info("Batch async pipeline started: userId={}, repos={}", userId, repositoryIds);

        // 엔티티를 비동기 컨텍스트에서 다시 로드 (영속성 컨텍스트 분리)
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.error("Batch pipeline aborted: user not found. userId={}", userId);
            repositoryIds.forEach(repoId ->
                    syncStatusService.setFailed(userId, repoId, "사용자를 찾을 수 없습니다."));
            // 사용자 조회 실패를 실패 로그에 기록
            failedJobRedisStore.push(userId, FailedJobRedisStore.JobType.GITHUB_ANALYSIS,
                    ErrorCode.AUTH_REQUIRED.name(), "배치 분석 실패: 사용자를 찾을 수 없습니다.");
            return;
        }

        // ── 1단계: 각 repo에 대해 ①②③ 병렬 실행 ────────────────────────
        // thread-safe list: 여러 CompletableFuture가 동시에 add()하므로 CopyOnWriteArrayList 사용
        List<GithubRepository> analyzedRepos = new CopyOnWriteArrayList<>();

        List<CompletableFuture<Void>> futures = repositoryIds.stream()
                .map(repositoryId -> CompletableFuture.runAsync(() -> {
                    // 스레드 추적 등록 (cancel() 호출 시 interrupt 가능)
                    String key = threadKey(userId, repositoryId);
                    activeThreads.put(key, Thread.currentThread());

                    // PENDING 중 cancel() 이 먼저 호출된 경우 즉시 skip
                    SyncStatusService.SyncStatusData currentStatus =
                            syncStatusService.getStatus(userId, repositoryId).orElse(null);
                    if (currentStatus == null || currentStatus.status() == SyncStatus.FAILED) {
                        log.info("Batch: repo skipped (cancelled before start): repoId={}", repositoryId);
                        activeThreads.remove(key);
                        return;
                    }

                    // findByIdWithConnection: githubConnection + user JOIN FETCH → LazyInitializationException 방지
                    GithubRepository repo = repositoryRepository.findByIdWithConnection(repositoryId).orElse(null);
                    if (repo == null) {
                        syncStatusService.setFailed(userId, repositoryId, "저장소를 찾을 수 없습니다.");
                        // 저장소 조회 실패를 실패 로그에 기록
                        failedJobRedisStore.push(userId, FailedJobRedisStore.JobType.GITHUB_ANALYSIS,
                                ErrorCode.GITHUB_REPOSITORY_NOT_FOUND.name(), "배치 분석 실패: 저장소를 찾을 수 없습니다.");
                        activeThreads.remove(key);
                        return;
                    }

                    try {
                        // ①②③ 실행. significance check 미달이면 false 반환 → 배치 AI 대상 제외
                        boolean passed = runStaticAnalysisSteps(user, repo, userId, repositoryId);
                        if (passed) {
                            analyzedRepos.add(repo);
                        }
                    } catch (Exception e) {
                        log.error("Batch: static analysis failed for repoId={}: {}", repositoryId, e.getMessage(), e);
                        syncStatusService.setFailed(userId, repositoryId, summarizeError(e));
                        // 정적 분석 실패를 실패 로그에 기록
                        failedJobRedisStore.push(userId, FailedJobRedisStore.JobType.GITHUB_ANALYSIS,
                                ErrorCode.GITHUB_COMMIT_SYNC_FAILED.name(), "정적 분석 실패: " + summarizeError(e));
                        try {
                            codeIndexService.deleteByRepository(repo);
                        } catch (Exception cleanupEx) {
                            log.warn("Batch: code index cleanup failed for repoId={}: {}",
                                    repositoryId, cleanupEx.getMessage());
                        }
                    } finally {
                        activeThreads.remove(key);
                        // 정적 분석 완료 후 clone 디렉터리 정리
                        // (BatchRepoSummaryGeneratorService.collectSingleRepoData가 clone 이후 읽으므로
                        //  분석 실패한 repo만 여기서 정리. 성공한 repo는 generateAll 완료 후 정리됨.)
                        if (!analyzedRepos.contains(repo)) {
                            repoCloneService.deleteRepo(userId, repositoryId);
                        }
                    }
                }, parallelAnalysisExecutor))
                .toList();

        // 모든 repo의 ①②③이 끝날 때까지 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (analyzedRepos.isEmpty()) {
            log.warn("Batch: no repos passed static analysis. Batch AI call skipped. userId={}", userId);
            return;
        }

        // ── 2단계: 단 1회 배치 AI 호출 ④ ──────────────────────────────
        // 정적 분석에 성공한 repo들의 authorEmail 결정 (첫 번째 repo 기준, 동일 사용자이므로 동일)
        String authorEmail = resolveAuthorEmail(analyzedRepos.get(0), user);
        String triggerReason = "significant_commits"; // 배치 분석은 항상 갱신 트리거

        try {
            // generateAll: 청크 단위 AI 호출로 처리 (partial recovery 포함)
            // 청크 시작 시점에 해당 chunk repos만 "summary"로 전환 (나머지는 ai_pending 유지)
            List<com.back.backend.domain.github.entity.RepoSummary> savedSummaries =
                    batchRepoSummaryGeneratorService.generateAll(user, analyzedRepos, authorEmail, triggerReason,
                            chunkRepos -> chunkRepos.forEach(repo ->
                                    syncStatusService.setInProgress(userId, repo.getId(), "summary")));
            mergedSummaryService.rebuild(user);

            // 실제로 저장된 repo ID 집합
            java.util.Set<Long> savedRepoIds = savedSummaries.stream()
                    .map(s -> s.getGithubRepository().getId())
                    .collect(java.util.stream.Collectors.toSet());

            // 저장된 repo만 COMPLETED, 저장 안 된 repo(partial recovery 탈락)는 FAILED
            analyzedRepos.forEach(repo -> {
                if (savedRepoIds.contains(repo.getId())) {
                    syncStatusService.setCompleted(userId, repo.getId());
                    significanceCheckService.markAnalyzed(userId, repo.getId());
                } else {
                    log.warn("Batch: repo summary not saved (partial recovery excluded): repoId={}", repo.getId());
                    syncStatusService.setFailed(userId, repo.getId(), "AI 분석 부분 실패: 토큰 초과로 요약을 생성하지 못했습니다.");
                }
            });

            log.info("Batch pipeline completed: userId={}, saved={}/{}", userId, savedRepoIds.size(), analyzedRepos.size());
            // 성공 시에만 clone 삭제 (용량 확보)
            analyzedRepos.forEach(repo ->
                    repoCloneService.deleteRepo(userId, repo.getId()));

        } catch (Exception e) {
            log.error("Batch: AI call failed for userId={}: {}", userId, e.getMessage(), e);
            // AI 호출 실패: 분석된 모든 repo를 FAILED 처리
            String errorMsg = "배치 AI 호출 실패: " + summarizeError(e);
            analyzedRepos.forEach(repo ->
                    syncStatusService.setFailed(userId, repo.getId(), errorMsg));
            // 배치 AI 호출 실패를 실패 로그에 기록 (repo 수에 관계없이 1건)
            failedJobRedisStore.push(userId, FailedJobRedisStore.JobType.GITHUB_ANALYSIS,
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE.name(), errorMsg);
            // CodeIndex · clone 유지 — 정적 분석 결과 보존, CloneDirectoryCleanupScheduler가 주기 청소
        }
    }

    /**
     * 진행 중인 분석 파이프라인을 취소한다.
     *
     * 실행 중인 스레드가 있으면 interrupt 신호를 보내고 Redis 상태를 FAILED로 갱신한다.
     * 스레드가 없어도 (이미 완료됐거나 등록 전이면) Redis 상태만 갱신한다.
     */
    public void cancel(Long userId, Long repositoryId) {
        String key = threadKey(userId, repositoryId);
        Thread t = activeThreads.remove(key);
        if (t != null && t.isAlive()) {
            t.interrupt();
            log.info("Analysis pipeline interrupted: userId={}, repoId={}", userId, repositoryId);
        }
        syncStatusService.getStatus(userId, repositoryId).ifPresent(s -> {
            if (s.status() == SyncStatus.PENDING || s.status() == SyncStatus.IN_PROGRESS) {
                syncStatusService.setFailed(userId, repositoryId, "분석이 취소되었습니다.");
            }
        });
    }

    /**
     * 여러 repo의 진행 중인 분석 파이프라인을 일괄 취소한다.
     *
     * 각 repo에 대해 {@link #cancel}을 순차 호출한다.
     * 개별 취소는 idempotent하므로 이미 완료된 repo가 포함돼도 안전하다.
     *
     * @param userId        인증된 사용자 ID
     * @param repositoryIds 취소할 repo ID 목록
     */
    public void cancelBatch(Long userId, List<Long> repositoryIds) {
        if (repositoryIds == null || repositoryIds.isEmpty()) return;
        for (Long repositoryId : repositoryIds) {
            try {
                cancel(userId, repositoryId);
            } catch (Exception e) {
                log.warn("Batch cancel partial failure: userId={}, repoId={}, reason={}",
                        userId, repositoryId, e.getMessage());
            }
        }
    }

    /**
     * 여러 repo를 배치로 분석하고 단 1회의 AI 호출로 요약을 생성한다.
     *
     * <p>기존 {@link #triggerAnalysis}는 repo마다 AI를 1회씩 호출했다.
     * 이 메서드는 모든 repo의 정적 분석(①②③)이 완료된 후 배치 AI 호출(④)을 1회만 수행하여
     * Gemini/Groq 무료 tier의 Rate Limit 소모를 최소화한다.
     *
     * <p>즉시 반환 (202 Accepted 패턴). 진행 상황은 각 repo의 sync-status 폴링으로 확인.
     *
     * @param userId        인증된 사용자 ID
     * @param repositoryIds 분석할 repo ID 목록 (1개 이상)
     * @throws ServiceException repo 미존재, 접근 권한 없음, 커밋 미동기화 등
     */
    @Transactional(readOnly = true)
    public void triggerBatchAnalysis(Long userId, List<Long> repositoryIds) {
        if (repositoryIds == null || repositoryIds.isEmpty()) {
            throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST, "분석할 저장소 목록이 비어있습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.AUTH_REQUIRED,
                        HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        // 각 repo에 대해 유효성 검증 + PENDING 등록
        for (Long repositoryId : repositoryIds) {
            GithubRepository repo = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.GITHUB_REPOSITORY_NOT_FOUND,
                            HttpStatus.NOT_FOUND, "저장소를 찾을 수 없습니다: " + repositoryId));

            if (!repo.getGithubConnection().getUser().getId().equals(userId)) {
                throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                        HttpStatus.FORBIDDEN, "접근 권한이 없는 저장소입니다: " + repositoryId);
            }

            // 이미 진행 중인 분석이 있으면 중복 방지
            syncStatusService.getStatus(userId, repositoryId).ifPresent(existing -> {
                if (existing.status() == SyncStatus.PENDING
                        || existing.status() == SyncStatus.IN_PROGRESS) {
                    throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                            HttpStatus.CONFLICT,
                            "이미 분석이 진행 중입니다: " + repositoryId);
                }
            });

            // 각 repo를 PENDING으로 등록
            // 정적 분석(병렬, 고정) + AI 청크 호출(청크 수 × 청크당 시간)
            int chunkCount = (int) Math.ceil(repositoryIds.size() / (double) DEFAULT_CHUNK_SIZE);
            long estimatedSeconds = (ESTIMATED_STATIC_MINUTES + chunkCount * ESTIMATED_AI_MINUTES_PER_CHUNK) * 60L;
            Instant estimatedEnd = Instant.now().plusSeconds(estimatedSeconds);
            syncStatusService.setPending(userId, repositoryId, estimatedEnd);
        }

        log.info("Batch analysis pipeline triggered: userId={}, repoCount={}",
                userId, repositoryIds.size());

        // @Async 프록시를 통해 비동기 실행
        self.executeBatchAsync(userId, repositoryIds);
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
        // 실행 중인 스레드 등록 (cancel() 호출 시 interrupt를 보낼 수 있도록)
        activeThreads.put(threadKey(userId, repositoryId), Thread.currentThread());

        // PENDING → 큐 대기 중 cancel()이 먼저 호출된 경우 (Redis가 이미 FAILED) 즉시 중단
        SyncStatusService.SyncStatusData currentStatus =
                syncStatusService.getStatus(userId, repositoryId).orElse(null);
        if (currentStatus == null || currentStatus.status() == SyncStatus.FAILED) {
            log.info("Pipeline skipped (cancelled before start): userId={}, repoId={}", userId, repositoryId);
            activeThreads.remove(threadKey(userId, repositoryId));
            return;
        }

        // 트랜잭션 외부에서 엔티티를 다시 로드 (영속성 컨텍스트 분리)
        User user = userRepository.findById(userId).orElse(null);
        // findByIdWithConnection: githubConnection + user JOIN FETCH → 트랜잭션 없는 비동기 컨텍스트에서
        // LazyInitializationException 없이 repo.getGithubConnection().getGithubLogin() 등을 사용 가능
        GithubRepository repo = repositoryRepository.findByIdWithConnection(repositoryId).orElse(null);

        if (user == null || repo == null) {
            log.error("Pipeline aborted: user or repo not found. userId={}, repoId={}", userId, repositoryId);
            syncStatusService.setFailed(userId, repositoryId, "사용자 또는 저장소를 찾을 수 없습니다.");
            // 엔티티 조회 실패를 실패 로그에 기록
            failedJobRedisStore.push(userId, FailedJobRedisStore.JobType.GITHUB_ANALYSIS,
                    ErrorCode.AUTH_REQUIRED.name(), "분석 실패: 사용자 또는 저장소를 찾을 수 없습니다.");
            activeThreads.remove(threadKey(userId, repositoryId));
            return;
        }

        try {
            runPipeline(user, repo, userId, repositoryId);
        } catch (Exception e) {
            log.error("Pipeline failed: userId={}, repoId={}, error={}", userId, repositoryId, e.getMessage(), e);
            syncStatusService.setFailed(userId, repositoryId, summarizeError(e));
            // 파이프라인 실패를 실패 로그에 기록
            failedJobRedisStore.push(userId, FailedJobRedisStore.JobType.GITHUB_ANALYSIS,
                    ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE.name(), "분석 파이프라인 실패: " + summarizeError(e));
        } finally {
            activeThreads.remove(threadKey(userId, repositoryId));
            repoCloneService.deleteRepo(userId, repositoryId);
        }
    }

    // ─────────────────────────────────────────────────
    // 파이프라인 단계
    // ─────────────────────────────────────────────────

    /**
     * 배치 파이프라인에서 각 repo에 대해 ①②③ (significance check, clone, static analysis)만 실행한다.
     *
     * <p>④ (AI 요약 생성)은 포함하지 않는다 — 모든 repo가 ①②③을 완료한 뒤
     * {@link BatchRepoSummaryGeneratorService#generateAll}에서 1회 배치 호출로 처리한다.
     *
     * @return significance check를 통과하면 true, 임계값 미달로 skip되면 false
     * @throws Exception 분석 단계에서 오류 발생 시 (호출자가 FAILED 처리)
     */
    boolean runStaticAnalysisSteps(User user, GithubRepository repo,
                                   Long userId, Long repositoryId) {
        // ① Significance Check
        syncStatusService.setInProgress(userId, repositoryId, "significance_check");
        Optional<String> skipReason = significanceCheckService.isSignificant(repo, userId);
        if (skipReason.isPresent()) {
            log.info("Batch: repo not significant, skipping: userId={}, repoId={}, reason={}",
                    userId, repositoryId, skipReason.get());
            syncStatusService.setSkipped(userId, repositoryId, skipReason.get());
            significanceCheckService.markAnalyzed(userId, repositoryId);
            return false; // 이 repo는 배치 AI 호출 대상에서 제외
        }

        // ② Clone / Fetch
        checkCancelled(userId, repositoryId);
        syncStatusService.setInProgress(userId, repositoryId, "clone");
        repoCloneService.cloneOrFetch(repo.getHtmlUrl(), userId, repositoryId);

        // ② 파일 필터링 + 시크릿 스캔
        Path repoPath = repoCloneService.getRepoPath(userId, repositoryId);
        Set<String> safeFiles = applyFileFilterAndSecretScan(repo, repoPath);

        // ③ 정적 분석 + PageRank + Code Index 저장
        checkCancelled(userId, repositoryId);
        syncStatusService.setInProgress(userId, repositoryId, "analysis");

        String authorEmail = resolveAuthorEmail(repo, user);
        Set<String> authoredFiles = contributionExtractorService.getAuthoredFiles(repoPath, authorEmail);

        boolean largeRepo = isLargeRepo(repoPath);
        Optional<Set<String>> analyzeScope;
        if (largeRepo) {
            Set<String> scopedFiles = authoredFiles.stream()
                    .filter(safeFiles::contains)
                    .collect(java.util.stream.Collectors.toSet());
            analyzeScope = Optional.of(scopedFiles);
            log.info("Batch: large repo detected (>{} files), scope=authored∩safe: repoId={}, count={}",
                    LARGE_REPO_FILE_THRESHOLD, repositoryId, scopedFiles.size());
        } else {
            analyzeScope = Optional.of(safeFiles);
        }

        List<AnalysisNode> nodes = staticAnalysisService.analyze(repoPath, analyzeScope);
        Map<String, Double> pagerankMap = callGraphService.computePageRank(nodes);
        codeIndexService.buildIndex(repo, userId, nodes, authoredFiles, pagerankMap);

        log.info("Batch: static analysis completed for repoId={}", repositoryId);
        syncStatusService.setInProgress(userId, repositoryId, "ai_pending");
        return true; // 이 repo를 배치 AI 호출 대상에 포함
    }

    private void runPipeline(User user, GithubRepository repo, Long userId, Long repositoryId) {

        // ① Significance Check
        syncStatusService.setInProgress(userId, repositoryId, "significance_check");
        Optional<String> skipReason = significanceCheckService.isSignificant(repo, userId);
        if (skipReason.isPresent()) {
            log.info("Pipeline skipped: userId={}, repoId={}, reason={}", userId, repositoryId, skipReason.get());
            syncStatusService.setSkipped(userId, repositoryId, skipReason.get());
            significanceCheckService.markAnalyzed(userId, repositoryId);
            return;
        }

        // ② Clone / Fetch
        checkCancelled(userId, repositoryId); // significance_check 완료 후 취소 여부 확인
        syncStatusService.setInProgress(userId, repositoryId, "clone");
        Path repoPath = repoCloneService.cloneOrFetch(repo.getHtmlUrl(), userId, repositoryId);

        // ② 파일 필터링 + 시크릿 스캔 (확장자/크기 필터 → Gitleaks 스캔 → 시크릿 파일 제거)
        Set<String> safeFiles = applyFileFilterAndSecretScan(repo, repoPath);

        // ③ 정적 분석 + PageRank + Code Index 저장
        checkCancelled(userId, repositoryId); // clone 완료 후 취소 여부 확인
        syncStatusService.setInProgress(userId, repositoryId, "analysis");

        String authorEmail = resolveAuthorEmail(repo, user);
        Set<String> authoredFiles = contributionExtractorService.getAuthoredFiles(repoPath, authorEmail);

        // large repo이면 본인 기여 파일과 safe files의 교집합만 분석
        // 일반 repo는 safe files 전체 분석
        boolean largeRepo = isLargeRepo(repoPath);
        Optional<Set<String>> analyzeScope;
        if (largeRepo) {
            Set<String> scopedFiles = authoredFiles.stream()
                    .filter(safeFiles::contains)
                    .collect(java.util.stream.Collectors.toSet());
            analyzeScope = Optional.of(scopedFiles);
            log.info("Large repo detected (>{} source files), scope=authored∩safe: userId={}, repoId={}, count={}",
                    LARGE_REPO_FILE_THRESHOLD, userId, repositoryId, scopedFiles.size());
        } else {
            analyzeScope = Optional.of(safeFiles);
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
        checkCancelled(userId, repositoryId); // analysis 완료 후 취소 여부 확인
        syncStatusService.setInProgress(userId, repositoryId, "summary");
        try {
            repoSummaryGeneratorService.generate(user, repo, authorEmail, triggerReason);
            mergedSummaryService.rebuild(user);
        } catch (Exception e) {
            // 요약 생성 실패 시 이번 run에서 새로 저장한 CodeIndex를 정리한다.
            // clone/분석 단계 실패는 이전 성공 run의 CodeIndex를 건드리지 않는다.
            try {
                codeIndexService.deleteByRepository(repo);
                log.info("Cleaned up code index after summary failure: repoId={}", repositoryId);
            } catch (Exception cleanupEx) {
                log.warn("Failed to cleanup code index: repoId={}, error={}", repositoryId, cleanupEx.getMessage());
            }
            throw e;
        }

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

    /**
     * 스레드 interrupt 또는 Redis FAILED 상태이면 CancellationException을 던진다.
     * 각 파이프라인 단계 사이에 호출하여 취소를 즉시 감지한다.
     *
     * interrupt 체크: cancel()이 t.interrupt()를 호출했을 때 감지
     * Redis 체크: interrupt 신호가 블로킹 구간에서 소비된 경우에도 취소를 감지
     */
    private void checkCancelled(Long userId, Long repositoryId) {
        boolean interrupted = Thread.currentThread().isInterrupted();
        if (!interrupted) {
            // Redis 상태로 2차 확인 (interrupt 신호가 이미 소비된 경우 대비)
            SyncStatusService.SyncStatusData status =
                    syncStatusService.getStatus(userId, repositoryId).orElse(null);
            if (status != null && status.status() == SyncStatus.FAILED) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt(); // interrupt 플래그 복원
            throw new CancellationException("분석이 취소되었습니다.");
        }
    }

    /**
     * 파일 확장자/크기 필터링과 시크릿 스캔을 순서대로 실행하고 안전한 파일의 상대 경로 집합을 반환한다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>{@link RepoFileFilterService}: 확장자 Whitelist + 단일 파일 크기 상한 필터링</li>
     *   <li>{@link GitleaksService}: 시크릿 스캔 — 발견된 파일을 분석 대상에서 제거</li>
     *   <li>시크릿 발견 시: 파일 경로 + 룰 ID를 {@code github_repositories.secret_excluded_files}에 저장</li>
     * </ol>
     *
     * <p>시크릿 발견 시에도 <b>분석은 계속 진행</b>된다 (Graceful Degradation).
     * 실제 시크릿 값은 저장하거나 로그에 남기지 않는다.</p>
     *
     * @param repo     분석 대상 GithubRepository
     * @param repoPath clone된 repo 루트 경로
     * @return 분석 가능한 파일의 상대 경로 집합 (repoPath 기준)
     */
    private Set<String> applyFileFilterAndSecretScan(GithubRepository repo, Path repoPath) {
        // 1) 확장자 Whitelist + 크기 필터
        RepoFileFilterService.FilterResult filterResult = repoFileFilterService.filter(repoPath);

        // 절대 경로 → 상대 경로(String) 변환 (OS 경로 구분자를 /로 통일)
        Set<String> safeFiles = filterResult.allowed().stream()
                .map(p -> repoPath.relativize(p).toString().replace('\\', '/'))
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));

        log.info("FileFilter: allowed={}, skipped={}, repoId={}",
                safeFiles.size(), filterResult.skipped().size(), repo.getId());

        // 2) 시크릿 스캔
        GitleaksService.GitleaksScanResult scanResult = gitleaksService.scanRepo(repoPath);

        if (!scanResult.hasFindings()) {
            // 시크릿 없음: 이전 분석에서 남은 excluded 기록 초기화 (재분석 시 stale 방지)
            repo.updateSecretExcludedFiles(null);
            repositoryRepository.save(repo);
            return safeFiles;
        }

        // 3) 시크릿 발견된 파일 제거 (경로 구분자 통일 후 비교)
        Set<String> secretFilePaths = scanResult.findings().stream()
                .map(f -> f.filePath().replace('\\', '/'))
                .collect(java.util.stream.Collectors.toSet());
        safeFiles.removeAll(secretFilePaths);

        log.warn("Secret scan: {} file(s) excluded from analysis. repoId={}, ruleIds={}",
                secretFilePaths.size(),
                repo.getId(),
                scanResult.findings().stream()
                        .map(GitleaksService.SecretFinding::ruleId)
                        .distinct()
                        .toList());

        // 4) 제외 파일 목록 DB 저장 (파일 경로 + 룰 ID만 — 시크릿 값 포함 없음)
        try {
            List<java.util.Map<String, String>> excluded = scanResult.findings().stream()
                    .map(f -> java.util.Map.of("filePath", f.filePath(), "ruleId", f.ruleId()))
                    .toList();
            repo.updateSecretExcludedFiles(objectMapper.writeValueAsString(excluded));
            repositoryRepository.save(repo);
        } catch (Exception e) {
            log.warn("Failed to persist secret excluded files for repoId={}: {}", repo.getId(), e.getMessage());
        }

        return safeFiles;
    }

    private String summarizeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    private static String threadKey(Long userId, Long repositoryId) {
        return userId + ":" + repositoryId;
    }
}
