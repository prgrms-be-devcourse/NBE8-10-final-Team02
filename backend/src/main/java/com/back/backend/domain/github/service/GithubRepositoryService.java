package com.back.backend.domain.github.service;

import com.back.backend.domain.github.analysis.AnalysisPipelineService;
import com.back.backend.domain.github.analysis.SyncStatusService;
import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.dto.response.RepoSyncStatusResponse;
import com.back.backend.domain.github.dto.response.RepositorySelectionResponse;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.portfolio.MergedSummaryService;
import com.back.backend.domain.github.repository.CodeIndexRepository;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.github.repository.RepoSummaryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.response.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 저장된 GitHub repo 목록 조회 및 선택 상태 관리를 담당한다.
 *
 * NOTE: 파일명 오류 (GithubRepositoryrService.java) 가 기존에 있음.
 * 올바른 클래스명은 GithubRepositoryService이며 이 파일을 사용한다.
 */
@Service
public class GithubRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GithubRepositoryService.class);

    private final GithubConnectionRepository connectionRepository;
    private final GithubRepositoryRepository repositoryRepository;
    private final GithubCommitRepository commitRepository;
    private final UserRepository userRepository;
    private final MergedSummaryService mergedSummaryService;
    private final AnalysisPipelineService analysisPipelineService;
    private final SyncStatusService syncStatusService;
    private final CodeIndexRepository codeIndexRepository;
    private final RepoSummaryRepository repoSummaryRepository;

    public GithubRepositoryService(
            GithubConnectionRepository connectionRepository,
            GithubRepositoryRepository repositoryRepository,
            GithubCommitRepository commitRepository,
            UserRepository userRepository,
            MergedSummaryService mergedSummaryService,
            @Lazy AnalysisPipelineService analysisPipelineService,
            SyncStatusService syncStatusService,
            CodeIndexRepository codeIndexRepository,
            RepoSummaryRepository repoSummaryRepository
    ) {
        this.connectionRepository = connectionRepository;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.userRepository = userRepository;
        this.mergedSummaryService = mergedSummaryService;
        this.analysisPipelineService = analysisPipelineService;
        this.syncStatusService = syncStatusService;
        this.codeIndexRepository = codeIndexRepository;
        this.repoSummaryRepository = repoSummaryRepository;
    }

    /**
     * 사용자의 저장된 repo 목록을 페이지네이션으로 반환한다.
     *
     * @param selected null이면 전체, true/false면 선택 여부로 필터
     */
    @Transactional(readOnly = true)
    public Page<GithubRepositoryResponse> getRepositories(Long userId, Boolean selected, int page, int size) {
        GithubConnection connection = findConnectionOrThrow(userId);

        // GitHub repo ID 내림차순 — 나중에 생성된 repo가 위에 오고, 선택/해제 시 순서가 바뀌지 않음
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("githubRepoId").descending());

        Page<GithubRepository> repoPage = (selected != null)
                ? repositoryRepository.findByGithubConnectionAndSelected(connection, selected, pageable)
                : repositoryRepository.findByGithubConnection(connection, pageable);

        // 이 페이지의 repo들 중 커밋이 있는 id 집합 (쿼리 1회 — N+1 방지)
        List<Long> pageRepoIds = repoPage.getContent().stream().map(GithubRepository::getId).toList();
        Set<Long> reposWithCommits = pageRepoIds.isEmpty()
                ? Set.of()
                : commitRepository.findRepositoryIdsWithCommits(pageRepoIds);

        // 이 페이지의 모든 repo 분석 상태를 Redis MGET 1회로 조회
        Map<Long, SyncStatusService.SyncStatusData> statusMap = pageRepoIds.isEmpty()
                ? Map.of()
                : syncStatusService.getStatusBulk(userId, pageRepoIds);

        return repoPage.map(repo -> {
            RepoSyncStatusResponse analysisStatus = statusMap.containsKey(repo.getId())
                    ? RepoSyncStatusResponse.from(statusMap.get(repo.getId()))
                    : null;
            return GithubRepositoryResponse.from(repo, reposWithCommits.contains(repo.getId()), analysisStatus);
        });
    }

    /**
     * 선택할 repo ID 목록을 받아 is_selected 상태를 일괄 저장한다.
     *
     * 기존 선택 상태를 전부 해제한 뒤 요청 목록만 선택 상태로 변경하는 방식이다.
     * 소유권 검사: 전달된 repo ID가 모두 현재 사용자 소유인지 확인한다.
     */
    @Transactional
    public RepositorySelectionResponse saveSelection(Long userId, List<Long> repositoryIds) {
        GithubConnection connection = findConnectionOrThrow(userId);

        // 현재 선택된 repo 전체를 해제
        List<GithubRepository> currentlySelected =
                repositoryRepository.findByGithubConnectionAndSelectedTrue(connection);
        Set<Long> newSelectedIds = new HashSet<>(repositoryIds);
        List<Long> deselectedRepoIds = currentlySelected.stream()
                .filter(repo -> !newSelectedIds.contains(repo.getId()))
                .map(GithubRepository::getId)
                .toList();
        currentlySelected.forEach(repo -> repo.updateSelection(false));

        // 요청한 repo들이 모두 이 사용자 소유인지 확인 후 선택 상태로 변경
        // 소유권 검사: connection 기준으로 조회했을 때 요청 수와 결과 수가 다르면 권한 위반
        List<GithubRepository> toSelect =
                repositoryRepository.findByGithubConnectionAndIdIn(connection, repositoryIds);

        if (toSelect.size() != repositoryIds.size()) {
            throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN, HttpStatus.FORBIDDEN,
                    "접근 권한이 없는 저장소가 포함되어 있습니다.");
        }

        toSelect.forEach(repo -> repo.updateSelection(true));

        // deselect가 발생한 경우, 트랜잭션 커밋 후:
        //   1. 진행 중인 분석 취소
        //   2. 관련 데이터(커밋, 코드 인덱스, repo 요약) 삭제
        //   3. MergedSummary 재집계
        // afterCommit 훅 사용 이유: 같은 트랜잭션 안에서 호출하면
        // IllegalStateException 발생 시 rollback-only 마킹으로 선택 저장까지 실패함
        if (!deselectedRepoIds.isEmpty()) {
            User user = userRepository.findById(userId).orElseThrow();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long repoId : deselectedRepoIds) {
                        try {
                            // 진행 중인 분석 스레드 취소
                            analysisPipelineService.cancel(userId, repoId);
                        } catch (Exception e) {
                            log.warn("Failed to cancel analysis for repoId={}: {}", repoId, e.getMessage());
                        }
                        try {
                            // 관련 데이터 삭제 (각각 새 트랜잭션으로 실행됨)
                            repoSummaryRepository.deleteByGithubRepositoryId(repoId);
                            codeIndexRepository.deleteByGithubRepositoryId(repoId);
                            commitRepository.deleteByRepositoryId(repoId);
                            log.info("Deleted data for deselected repoId={}", repoId);
                        } catch (Exception e) {
                            log.warn("Failed to delete data for repoId={}: {}", repoId, e.getMessage());
                        }
                    }
                    try {
                        mergedSummaryService.rebuild(user);
                    } catch (IllegalStateException e) {
                        log.debug("No summaries to rebuild after deselection: userId={}", userId);
                    } catch (Exception e) {
                        log.warn("Failed to rebuild merged summary after deselection: userId={}, error={}",
                                userId, e.getMessage());
                    }
                }
            });
        }

        List<Long> selectedIds = toSelect.stream().map(GithubRepository::getId).toList();
        return RepositorySelectionResponse.of(selectedIds);
    }

    /**
     * 기여 repo를 github_repositories에서 완전히 삭제한다.
     * 소유권 검사: 해당 repo가 현재 사용자의 connection에 속하는지 확인.
     * 진행 중인 분석이 있으면 취소하고, 관련 데이터(커밋, 코드 인덱스, repo 요약)도 삭제한다.
     */
    @Transactional
    public void deleteRepository(Long userId, Long repositoryId) {
        GithubConnection connection = findConnectionOrThrow(userId);

        GithubRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "repository를 찾을 수 없습니다."));

        if (!repo.getGithubConnection().getId().equals(connection.getId())) {
            throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN, HttpStatus.FORBIDDEN,
                    "접근 권한이 없는 repository입니다.");
        }

        User user = userRepository.findById(userId).orElseThrow();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { analysisPipelineService.cancel(userId, repositoryId); }
                catch (Exception e) { log.warn("Failed to cancel analysis for repoId={}: {}", repositoryId, e.getMessage()); }
                try {
                    repoSummaryRepository.deleteByGithubRepositoryId(repositoryId);
                    codeIndexRepository.deleteByGithubRepositoryId(repositoryId);
                    commitRepository.deleteByRepositoryId(repositoryId);
                } catch (Exception e) {
                    log.warn("Failed to delete data for repoId={}: {}", repositoryId, e.getMessage());
                }
                try { mergedSummaryService.rebuild(user); }
                catch (Exception e) { log.warn("Failed to rebuild merged summary: userId={}", userId); }
            }
        });

        repositoryRepository.delete(repo);
        log.info("Deleted repository id={} for userId={}", repositoryId, userId);
    }

    // ─────────────────────────────────────────────────
    // Private helper
    // ─────────────────────────────────────────────────

    /**
     * 사용자의 GitHub OAuth 연결을 조회한다.
     * 연결이 없거나 OAuth token이 없으면 예외를 던진다.
     */
    private GithubConnection findConnectionOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        GithubConnection connection = connectionRepository.findByUser(user)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.GITHUB_CONNECTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "GitHub 연결 정보가 없습니다. GitHub OAuth로 연동해주세요."));

        if (connection.getAccessToken() == null) {
            throw new ServiceException(ErrorCode.GITHUB_SCOPE_INSUFFICIENT, HttpStatus.FORBIDDEN,
                    "GitHub OAuth 연동이 필요합니다. GitHub 계정을 연동해주세요.");
        }

        return connection;
    }
}