package com.back.backend.domain.github.controller;

import com.back.backend.domain.github.dto.request.AddContributionByUrlRequest;
import com.back.backend.domain.github.dto.request.GithubConnectRequest;
import com.back.backend.domain.github.dto.request.RepositorySelectionRequest;
import com.back.backend.domain.github.dto.request.SaveContributionRequest;
import com.back.backend.domain.github.dto.response.ContributedRepoResponse;
import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.dto.response.GithubConnectionResponse;
import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.dto.response.RepoSyncStatusResponse;
import com.back.backend.domain.github.dto.response.RepositorySelectionResponse;
import com.back.backend.domain.github.dto.response.RepositorySyncResponse;
import com.back.backend.domain.github.analysis.AnalysisPipelineService;
import com.back.backend.domain.github.analysis.SyncStatusService;
import com.back.backend.domain.github.service.GithubConnectionService;
import com.back.backend.domain.github.service.GithubDiscoveryService;
import com.back.backend.domain.github.service.GithubRepositoryService;
import com.back.backend.domain.github.service.GithubSyncService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.response.Pagination;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GitHub 연동 관련 API 엔드포인트.
 *
 * Controller 책임:
 *   - HTTP 요청/응답 처리와 @Valid 검증만 담당한다.
 *   - 비즈니스 로직은 Service에 위임한다.
 *   - 현재 사용자 ID는 Security Context(JwtAuthenticationToken)에서 꺼낸다.
 *     요청 바디나 query param으로 userId를 받지 않는다 (backend-conventions.md §7.4).
 */
@RestController
@RequestMapping("/api/v1/github")
public class GithubController {

    private final GithubConnectionService connectionService;
    private final GithubRepositoryService repositoryService;
    private final GithubSyncService syncService;
    private final GithubDiscoveryService discoveryService;
    private final SyncStatusService syncStatusService;
    private final AnalysisPipelineService analysisPipelineService;

    public GithubController(
            GithubConnectionService connectionService,
            GithubRepositoryService repositoryService,
            GithubSyncService syncService,
            GithubDiscoveryService discoveryService,
            SyncStatusService syncStatusService,
            AnalysisPipelineService analysisPipelineService
    ) {
        this.connectionService = connectionService;
        this.repositoryService = repositoryService;
        this.syncService = syncService;
        this.discoveryService = discoveryService;
        this.syncStatusService = syncStatusService;
        this.analysisPipelineService = analysisPipelineService;
    }

    /**
     * 현재 사용자의 GitHub 연결 정보 조회.
     *
     * 연결이 있으면 200 + 연결 정보, 없으면 200 + data: null 반환.
     * 프론트에서 /portfolio/github 진입 시 이미 연결됐는지 확인하는 데 사용한다.
     */
    @GetMapping("/connections")
    public ResponseEntity<ApiResponse<GithubConnectionResponse>> getConnection(
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        GithubConnectionResponse response = connectionService.getConnectionOrNull(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GitHub 연결 생성 또는 갱신.
     *
     * mode=oauth: GitHub OAuth 완료 후 token과 함께 호출.
     * Google/Kakao 로그인 사용자도 GitHub 기능 사용 시 이 API를 통해 OAuth 연동해야 한다.
     *
     * 201 Created 반환.
     */
    @PostMapping("/connections")
    public ResponseEntity<ApiResponse<GithubConnectionResponse>> createConnection(
            Authentication authentication,
            @RequestBody @Valid GithubConnectRequest request
    ) {
        Long userId = extractUserId(authentication);
        GithubConnectionResponse response = connectionService.createOrUpdateConnection(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * 저장된 GitHub OAuth 연결로 repo 목록 갱신.
     *
     * GitHub OAuth 로그인 사용자가 로그인 시 저장된 token을 사용해
     * GitHub API에서 repo 목록을 가져오고 DB에 반영한다.
     * 로그인 시점에는 repo를 가져오지 않으므로, 사용자가 명시적으로 이 API를 호출해야 한다.
     *
     * 202 Accepted 반환 (처리 시간이 있음).
     */
    @PostMapping("/connections/refresh")
    public ResponseEntity<ApiResponse<GithubConnectionResponse>> refreshConnection(
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        GithubConnectionResponse response = connectionService.refreshFromStoredConnection(userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    /**
     * 저장된 repo 목록 조회.
     *
     * selected 파라미터로 선택 여부 필터링 가능.
     * 기본 정렬: syncedAt desc (최근 동기화 기준).
     */
    @GetMapping("/repositories")
    public ResponseEntity<ApiResponse<List<GithubRepositoryResponse>>> getRepositories(
            Authentication authentication,
            @RequestParam(required = false) Boolean selected,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = extractUserId(authentication);
        Page<GithubRepositoryResponse> result = repositoryService.getRepositories(userId, selected, page, size);

        Pagination pagination = new Pagination(page, size,
                result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), pagination));
    }

    /**
     * 사용자가 선택한 repo 상태 저장.
     *
     * 기존 선택 상태를 전부 해제하고 요청 목록만 선택 상태로 변경한다.
     * 빈 리스트 전달 시 전체 해제.
     */
    @PutMapping("/repositories/selection")
    public ResponseEntity<ApiResponse<RepositorySelectionResponse>> updateSelection(
            Authentication authentication,
            @RequestBody @Valid RepositorySelectionRequest request
    ) {
        Long userId = extractUserId(authentication);
        RepositorySelectionResponse response = repositoryService.saveSelection(userId, request.repositoryIds());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 선택한 repo의 커밋 동기화 시작.
     *
     * 동기화 요청을 받고 202 Accepted를 즉시 반환한다.
     * private repo는 GitHub OAuth 추가 동의(repo scope)가 있는 경우에만 허용한다.
     *
     * TODO: 현재는 동기 처리. 데이터가 많아지면 비동기 처리(메시지 큐 등)로 전환을 고려한다.
     */
    @PostMapping("/repositories/{repositoryId}/sync-commits")
    public ResponseEntity<ApiResponse<RepositorySyncResponse>> syncCommits(
            Authentication authentication,
            @PathVariable Long repositoryId
    ) {
        Long userId = extractUserId(authentication);
        // 동기화 실행 (현재는 동기 처리)
        syncService.syncCommits(userId, repositoryId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(RepositorySyncResponse.queued(repositoryId)));
    }

    /**
     * 사용자가 기여한 public repo 목록을 조회한다.
     * GitHub OAuth 연동이 있어야 한다. alreadySaved로 이미 저장된 repo를 구분할 수 있다.
     *
     * @param yearsOffset 0=최근 2년(기본값), 1=2~4년 전, 2=4~6년 전
     */
    @GetMapping("/contributions/discovered")
    public ResponseEntity<ApiResponse<List<ContributedRepoResponse>>> getDiscoveredContributions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int yearsOffset
    ) {
        Long userId = extractUserId(authentication);
        List<ContributedRepoResponse> response = discoveryService.findContributedRepos(userId, yearsOffset);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 기여 탐색 목록에서 선택한 repo를 저장한다.
     * 이미 저장된 경우 기존 레코드를 그대로 반환한다.
     * 201 Created 반환.
     */
    @PostMapping("/contributions/save")
    public ResponseEntity<ApiResponse<GithubRepositoryResponse>> saveContribution(
            Authentication authentication,
            @RequestBody @Valid SaveContributionRequest request
    ) {
        Long userId = extractUserId(authentication);
        GithubRepositoryResponse response = discoveryService.saveContributionRepo(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * 사용자가 직접 입력한 GitHub URL로 기여 repo를 추가한다.
     * 본인 커밋이 존재하는 경우에만 저장되며, 본인 확인은 GitHub API로 수행한다.
     * 201 Created 반환.
     */
    @PostMapping("/contributions/add-by-url")
    public ResponseEntity<ApiResponse<GithubRepositoryResponse>> addContributionByUrl(
            Authentication authentication,
            @RequestBody @Valid AddContributionByUrlRequest request
    ) {
        Long userId = extractUserId(authentication);
        GithubRepositoryResponse response = discoveryService.verifyAndAddContributionByUrl(userId, request.url());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * 분석 파이프라인의 진행 상태를 조회한다.
     *
     * Redis에 저장된 sync:status:{userId}:{repositoryId} 값을 반환한다.
     * 상태가 없으면 (분석 미요청 또는 TTL 만료) data: null 반환.
     *
     * 클라이언트는 이 API를 폴링하여 PENDING/IN_PROGRESS 상태가 완료될 때까지 대기할 수 있다.
     */
    @GetMapping("/repositories/{repositoryId}/sync-status")
    public ResponseEntity<ApiResponse<RepoSyncStatusResponse>> getSyncStatus(
            Authentication authentication,
            @PathVariable Long repositoryId
    ) {
        Long userId = extractUserId(authentication);
        RepoSyncStatusResponse response = syncStatusService.getStatus(userId, repositoryId)
                .map(RepoSyncStatusResponse::from)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 분석 파이프라인(clone → 정적 분석 → Code Index → RepoSummary → MergedSummary)을 비동기로 시작한다.
     *
     * 즉시 202 Accepted와 현재 sync 상태를 반환한다.
     * 진행 상황은 GET /repositories/{repositoryId}/sync-status 폴링으로 확인한다.
     */
    @PostMapping("/repositories/{repositoryId}/analyze")
    public ResponseEntity<ApiResponse<RepoSyncStatusResponse>> analyzeRepository(
            Authentication authentication,
            @PathVariable Long repositoryId
    ) {
        Long userId = extractUserId(authentication);
        analysisPipelineService.triggerAnalysis(userId, repositoryId);

        // 방금 등록된 PENDING 상태 반환
        RepoSyncStatusResponse response = syncStatusService.getStatus(userId, repositoryId)
                .map(RepoSyncStatusResponse::from)
                .orElse(null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    // ─────────────────────────────────────────────────
    // Private helper
    // ─────────────────────────────────────────────────

    /**
     * Security Context의 JwtAuthenticationToken에서 userId를 꺼낸다.
     * 인증이 안 된 요청은 Security Filter에서 이미 차단되므로 여기서는 단순 캐스팅.
     */
    private Long extractUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}