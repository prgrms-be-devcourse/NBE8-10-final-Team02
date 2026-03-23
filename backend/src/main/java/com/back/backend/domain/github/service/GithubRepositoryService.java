package com.back.backend.domain.github.service;

import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.dto.response.RepositorySelectionResponse;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.response.Pagination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 저장된 GitHub repo 목록 조회 및 선택 상태 관리를 담당한다.
 *
 * NOTE: 파일명 오류 (GithubRepositoryrService.java) 가 기존에 있음.
 * 올바른 클래스명은 GithubRepositoryService이며 이 파일을 사용한다.
 */
@Service
public class GithubRepositoryService {

    private final GithubConnectionRepository connectionRepository;
    private final GithubRepositoryRepository repositoryRepository;
    private final UserRepository userRepository;

    public GithubRepositoryService(
            GithubConnectionRepository connectionRepository,
            GithubRepositoryRepository repositoryRepository,
            UserRepository userRepository
    ) {
        this.connectionRepository = connectionRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * 사용자의 저장된 repo 목록을 페이지네이션으로 반환한다.
     *
     * @param selected null이면 전체, true/false면 선택 여부로 필터
     */
    @Transactional(readOnly = true)
    public Page<GithubRepositoryResponse> getRepositories(Long userId, Boolean selected, int page, int size) {
        GithubConnection connection = findConnectionOrThrow(userId);

        // 최근 동기화 시간 기준 내림차순 정렬
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("syncedAt").descending());

        Page<GithubRepository> repoPage = (selected != null)
                ? repositoryRepository.findByGithubConnectionAndSelected(connection, selected, pageable)
                : repositoryRepository.findByGithubConnection(connection, pageable);

        return repoPage.map(GithubRepositoryResponse::from);
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

        List<Long> selectedIds = toSelect.stream().map(GithubRepository::getId).toList();
        return RepositorySelectionResponse.of(selectedIds);
    }

    // ─────────────────────────────────────────────────
    // Private helper
    // ─────────────────────────────────────────────────

    /**
     * 사용자의 GitHub 연결을 조회한다. 연결이 없으면 예외를 던진다.
     */
    private GithubConnection findConnectionOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return connectionRepository.findByUser(user)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.GITHUB_CONNECTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "GitHub 연결 정보가 없습니다. 먼저 GitHub를 연동해 주세요."));
    }
}