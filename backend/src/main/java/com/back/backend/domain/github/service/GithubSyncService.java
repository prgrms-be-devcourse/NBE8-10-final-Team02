package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.GithubCommit;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 선택된 repo의 commit을 GitHub API에서 가져와 DB에 저장한다.
 *
 * 핵심 규칙:
 *   - GitHub API 호출은 트랜잭션 밖에서 먼저 수행한다.
 *   - is_user_commit = true 조건: commit.author.login == githubLogin
 *   - 동일 sha의 commit은 중복 저장하지 않는다 (uk_github_commits_repository_sha).
 *   - private repo 접근 시 connection의 accessToken에 repo scope가 있어야 한다.
 */
@Service
public class GithubSyncService {

    private static final Logger log = LoggerFactory.getLogger(GithubSyncService.class);

    private final GithubApiClient githubApiClient;
    private final GithubRepositoryRepository repositoryRepository;
    private final GithubCommitRepository commitRepository;
    private final GithubConnectionRepository connectionRepository;
    private final UserRepository userRepository;

    public GithubSyncService(
            GithubApiClient githubApiClient,
            GithubRepositoryRepository repositoryRepository,
            GithubCommitRepository commitRepository,
            GithubConnectionRepository connectionRepository,
            UserRepository userRepository
    ) {
        this.githubApiClient = githubApiClient;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
    }

    /**
     * 특정 repo의 commit을 동기화한다.
     *
     * 1. repo 소유권 확인 (userId → connection → repo)
     * 2. private repo이면 access_scope 확인
     * 3. GitHub API로 commit 목록 조회 (트랜잭션 밖)
     * 4. DB에 commit 저장 (트랜잭션 안)
     *
     * @param userId       현재 로그인 사용자
     * @param repositoryId 동기화할 github_repositories.id
     */
    public void syncCommits(Long userId, Long repositoryId) {
        // 1. repo 조회 및 소유권 확인
        GithubRepository repository = findRepositoryAndVerifyOwnership(userId, repositoryId);
        GithubConnection connection = repository.getGithubConnection();

        // 2. private repo이면 token에 repo scope가 있어야 한다
        if (repository.getVisibility().getValue().equals("private")
                || repository.getVisibility().getValue().equals("internal")) {
            if (connection.getAccessScope() == null
                    || !connection.getAccessScope().contains("repo")) {
                throw new ServiceException(ErrorCode.GITHUB_SCOPE_INSUFFICIENT, HttpStatus.FORBIDDEN,
                        "private repository 조회 권한이 부족합니다.");
            }
        }

        log.info("Starting commit sync for repositoryId={}, repo={}", repositoryId, repository.getFullName());

        // 3. GitHub API 호출 — 트랜잭션 밖에서 수행
        String[] parts = repository.getFullName().split("/");  // "owner/repo" 형식
        String owner = parts[0];
        String repo = parts[1];

        List<GithubApiClient.GithubCommitInfo> commits = githubApiClient.getCommits(
                owner, repo, connection.getGithubLogin(), connection.getAccessToken());

        log.info("Fetched {} commits from GitHub for repo={}", commits.size(), repository.getFullName());

        // 4. DB 저장 — GitHub API 결과를 받은 뒤 트랜잭션 시작
        saveCommits(repository, commits, connection.getGithubLogin());
    }

    // ─────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────

    /**
     * commit 목록을 DB에 저장한다.
     * 이미 저장된 sha는 건너뛴다 (uk_github_commits_repository_sha 기반 멱등 처리).
     *
     * is_user_commit 판단:
     *   commit.author.login == connection.githubLogin 이면 true
     *   GitHub 계정이 없는 commit(author == null)은 false로 저장한다.
     */
    @Transactional
    protected void saveCommits(GithubRepository repository,
                               List<GithubApiClient.GithubCommitInfo> commits,
                               String githubLogin) {
        int savedCount = 0;

        for (GithubApiClient.GithubCommitInfo commitInfo : commits) {
            // 이미 저장된 commit은 건너뜀 (중복 방지)
            boolean alreadyExists = commitRepository
                    .findByRepositoryAndGithubCommitSha(repository, commitInfo.sha())
                    .isPresent();

            if (alreadyExists) continue;

            // author.login 기반으로 사용자 본인 commit 여부 판단
            boolean isUserCommit = githubLogin.equals(commitInfo.authorLogin());

            commitRepository.save(GithubCommit.builder()
                    .repository(repository)
                    .githubCommitSha(commitInfo.sha())
                    .commitMessage(commitInfo.message())
                    .authorLogin(commitInfo.authorLogin())
                    .authorName(commitInfo.authorName())
                    .authorEmail(commitInfo.authorEmail())
                    .userCommit(isUserCommit)
                    .committedAt(commitInfo.committedAt())
                    .build());

            savedCount++;
        }

        log.info("Commit sync complete for repo={}: {} new commits saved", repository.getFullName(), savedCount);
    }

    /**
     * repo를 조회하고 현재 사용자 소유인지 확인한다.
     * 소유권 검사: repo → connection → user.id == 요청 userId
     */
    private GithubRepository findRepositoryAndVerifyOwnership(Long userId, Long repositoryId) {
        GithubRepository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.GITHUB_REPOSITORY_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "저장소를 찾을 수 없습니다."));

        Long ownerUserId = repository.getGithubConnection().getUser().getId();
        if (!ownerUserId.equals(userId)) {
            // path의 repositoryId만 믿지 말고 소유권을 검증한다 (backend-conventions.md §7.4)
            throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN, HttpStatus.FORBIDDEN,
                    "접근 권한이 없는 저장소입니다.");
        }

        return repository;
    }
}