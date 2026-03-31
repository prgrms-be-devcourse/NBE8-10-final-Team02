package com.back.backend.domain.github.service;

import com.back.backend.domain.github.dto.request.SaveContributionRequest;
import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.github.entity.RepositoryVisibility;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GithubDiscoveryServiceTest {

    @Mock
    private GithubApiClient githubApiClient;

    @Mock
    private GithubConnectionRepository connectionRepository;

    @Mock
    private GithubRepositoryRepository repositoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GithubDiscoveryService service;

    private User user;
    private GithubConnection connection;
    private GithubRepository savedRepo;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@example.com")
                .displayName("Test User")
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        connection = GithubConnection.builder()
                .user(user)
                .githubUserId(100001L)
                .githubLogin("github-user")
                .accessToken("test-token")
                .accessScope("repo,user:email")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(connection, "id", 10L);

        savedRepo = GithubRepository.builder()
                .githubConnection(connection)
                .githubRepoId(200001L)
                .ownerLogin("other-user")
                .repoName("contributed-repo")
                .fullName("other-user/contributed-repo")
                .htmlUrl("https://github.com/other-user/contributed-repo")
                .visibility(RepositoryVisibility.PUBLIC)
                .defaultBranch(null)
                .selected(false)
                .ownerType("collaborator")
                .syncedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(savedRepo, "id", 50L);
    }

    // ─────────────────────────────────────────────────
    // findContributedRepos
    // ─────────────────────────────────────────────────

    @Test
    void findContributedRepos_throwsForbiddenWhenNoConnection() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findContributedRepos(1L, 0))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException se = (ServiceException) ex;
                    assertThat(se.getErrorCode()).isEqualTo(ErrorCode.GITHUB_SCOPE_INSUFFICIENT);
                    assertThat(se.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void findContributedRepos_returnsEmptyListWhenNoContributions() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));
        given(githubApiClient.getContributedRepos("test-token", 0)).willReturn(List.of());

        List<?> result = service.findContributedRepos(1L, 0);

        assertThat(result).isEmpty();
        verify(repositoryRepository, never()).findByGithubConnectionAndGithubRepoIdIn(any(), any());
    }

    @Test
    void findContributedRepos_marksAlreadySavedRepos() {
        GithubApiClient.GithubContributedRepo contributedRepo = new GithubApiClient.GithubContributedRepo(
                200001L, "other-user/contributed-repo",
                "https://github.com/other-user/contributed-repo",
                "Java", 512, 5
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));
        given(githubApiClient.getContributedRepos("test-token", 0)).willReturn(List.of(contributedRepo));
        given(repositoryRepository.findByGithubConnectionAndGithubRepoIdIn(connection, List.of(200001L)))
                .willReturn(List.of(savedRepo));

        var result = service.findContributedRepos(1L, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).alreadySaved()).isTrue();
    }

    // ─────────────────────────────────────────────────
    // saveContributionRepo
    // ─────────────────────────────────────────────────

    @Test
    void saveContributionRepo_returnsExistingWhenAlreadySaved() {
        SaveContributionRequest request = new SaveContributionRequest(
                200001L, "other-user/contributed-repo",
                "https://github.com/other-user/contributed-repo",
                "Java", 512
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));
        given(repositoryRepository.findByGithubConnectionAndGithubRepoId(connection, 200001L))
                .willReturn(Optional.of(savedRepo));

        GithubRepositoryResponse result = service.saveContributionRepo(1L, request);

        assertThat(result.id()).isEqualTo(50L);
        verify(repositoryRepository, never()).save(any());
    }

    @Test
    void saveContributionRepo_savesNewRepoWhenNotYetSaved() {
        SaveContributionRequest request = new SaveContributionRequest(
                200002L, "other-user/new-repo",
                "https://github.com/other-user/new-repo",
                "TypeScript", 256
        );

        GithubRepository newRepo = GithubRepository.builder()
                .githubConnection(connection)
                .githubRepoId(200002L)
                .ownerLogin("other-user")
                .repoName("new-repo")
                .fullName("other-user/new-repo")
                .htmlUrl("https://github.com/other-user/new-repo")
                .visibility(RepositoryVisibility.PUBLIC)
                .defaultBranch(null)
                .selected(false)
                .ownerType("collaborator")
                .syncedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(newRepo, "id", 51L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));
        given(repositoryRepository.findByGithubConnectionAndGithubRepoId(connection, 200002L))
                .willReturn(Optional.empty());
        given(repositoryRepository.save(any(GithubRepository.class))).willReturn(newRepo);

        GithubRepositoryResponse result = service.saveContributionRepo(1L, request);

        assertThat(result.id()).isEqualTo(51L);
        assertThat(result.repoName()).isEqualTo("new-repo");
        verify(repositoryRepository).save(any(GithubRepository.class));
    }

    // ─────────────────────────────────────────────────
    // verifyAndAddContributionByUrl
    // ─────────────────────────────────────────────────

    @Test
    void verifyAndAddContributionByUrl_throwsBadRequestWhenUrlIsInvalid() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.verifyAndAddContributionByUrl(1L, "not-a-github-url"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException se = (ServiceException) ex;
                    assertThat(se.getErrorCode()).isEqualTo(ErrorCode.GITHUB_URL_INVALID);
                    assertThat(se.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void verifyAndAddContributionByUrl_throwsUnprocessableWhenNoUserCommits() {
        String url = "https://github.com/other-user/contributed-repo";
        GithubApiClient.GithubRepoDetail detail = new GithubApiClient.GithubRepoDetail(
                200001L, "other-user/contributed-repo", "other-user", "contributed-repo",
                url, "main", 512
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));
        given(githubApiClient.getRepoDetail("other-user", "contributed-repo", "test-token"))
                .willReturn(detail);
        given(githubApiClient.countUserCommits("other-user", "contributed-repo", "github-user", "test-token"))
                .willReturn(0);

        assertThatThrownBy(() -> service.verifyAndAddContributionByUrl(1L, url))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException se = (ServiceException) ex;
                    assertThat(se.getErrorCode()).isEqualTo(ErrorCode.GITHUB_USER_COMMIT_NOT_IDENTIFIED);
                    assertThat(se.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
    }

    @Test
    void verifyAndAddContributionByUrl_savesRepoWhenUserHasCommits() {
        String url = "https://github.com/other-user/contributed-repo";
        GithubApiClient.GithubRepoDetail detail = new GithubApiClient.GithubRepoDetail(
                200001L, "other-user/contributed-repo", "other-user", "contributed-repo",
                url, "main", 512
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));
        given(githubApiClient.getRepoDetail("other-user", "contributed-repo", "test-token"))
                .willReturn(detail);
        given(githubApiClient.countUserCommits("other-user", "contributed-repo", "github-user", "test-token"))
                .willReturn(3);
        given(repositoryRepository.findByGithubConnectionAndGithubRepoId(connection, 200001L))
                .willReturn(Optional.empty());
        given(repositoryRepository.save(any(GithubRepository.class))).willReturn(savedRepo);

        GithubRepositoryResponse result = service.verifyAndAddContributionByUrl(1L, url);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(50L);
        verify(repositoryRepository).save(any(GithubRepository.class));
    }

    @Test
    void verifyAndAddContributionByUrl_returnsExistingWhenAlreadySaved() {
        String url = "https://github.com/other-user/contributed-repo";
        GithubApiClient.GithubRepoDetail detail = new GithubApiClient.GithubRepoDetail(
                200001L, "other-user/contributed-repo", "other-user", "contributed-repo",
                url, "main", 512
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connection));
        given(githubApiClient.getRepoDetail("other-user", "contributed-repo", "test-token"))
                .willReturn(detail);
        given(githubApiClient.countUserCommits("other-user", "contributed-repo", "github-user", "test-token"))
                .willReturn(2);
        given(repositoryRepository.findByGithubConnectionAndGithubRepoId(connection, 200001L))
                .willReturn(Optional.of(savedRepo));

        GithubRepositoryResponse result = service.verifyAndAddContributionByUrl(1L, url);

        assertThat(result.id()).isEqualTo(50L);
        verify(repositoryRepository, never()).save(any());
    }
}
