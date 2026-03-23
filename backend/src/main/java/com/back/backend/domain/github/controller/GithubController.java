package com.back.backend.domain.github.controller;

import com.back.backend.domain.github.dto.request.GithubConnectRequest;
import com.back.backend.domain.github.dto.request.RepositorySelectionRequest;
import com.back.backend.domain.github.dto.response.GithubConnectionResponse;
import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.dto.response.RepositorySelectionResponse;
import com.back.backend.domain.github.dto.response.RepositorySyncResponse;
import com.back.backend.domain.github.service.GithubConnectionService;
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
@RequestMapping("/github")
public class GithubController {

    private final GithubConnectionService connectionService;
    private final GithubRepositoryService repositoryService;
    private final GithubSyncService syncService;

    public GithubController(
            GithubConnectionService connectionService,
            GithubRepositoryService repositoryService,
            GithubSyncService syncService
    ) {
        this.connectionService = connectionService;
        this.repositoryService = repositoryService;
        this.syncService = syncService;
    }

    /**
     * GitHub 연결 생성 또는 갱신.
     *
     * mode=oauth: GitHub OAuth 완료 후 token과 함께 호출.
     * mode=url:   GitHub login을 직접 입력해 public repo만 연동.
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