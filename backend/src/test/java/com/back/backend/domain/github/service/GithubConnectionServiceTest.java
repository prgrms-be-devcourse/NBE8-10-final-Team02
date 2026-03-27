package com.back.backend.domain.github.service;

import com.back.backend.domain.github.dto.request.GithubConnectRequest;
import com.back.backend.domain.github.dto.response.GithubConnectionResponse;
import com.back.backend.domain.github.entity.GithubConnection;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GithubConnectionServiceTest {

    @Mock
    private GithubApiClient githubApiClient;

    @Mock
    private GithubConnectionRepository connectionRepository;

    @Mock
    private GithubRepositoryRepository repositoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GithubConnectionService service;

    private User user;
    private GithubConnection existingConnection;

    @BeforeEach
    void setUp() {
        // self 주입을 실제 프록시 없이 동작하게 하기 위해 자기 자신을 주입한다.
        // @Transactional self-invocation은 통합 테스트에서 검증되므로 단위 테스트에서는 직접 호출을 사용한다.
        ReflectionTestUtils.setField(service, "self", service);

        user = User.builder()
                .email("test@example.com")
                .displayName("Test User")
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        existingConnection = GithubConnection.builder()
                .user(user)
                .githubUserId(100001L)
                .githubLogin("github-user")
                .accessToken("old-token")
                .accessScope("repo")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(existingConnection, "id", 10L);
    }

    // ─────────────────────────────────────────────────
    // getConnectionOrNull
    // ─────────────────────────────────────────────────

    @Test
    void getConnectionOrNull_returnsResponseWhenConnected() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(existingConnection));

        GithubConnectionResponse result = service.getConnectionOrNull(1L);

        assertThat(result).isNotNull();
        assertThat(result.githubLogin()).isEqualTo("github-user");
        assertThat(result.githubUserId()).isEqualTo(100001L);
    }

    @Test
    void getConnectionOrNull_returnsNullWhenUserNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        GithubConnectionResponse result = service.getConnectionOrNull(99L);

        assertThat(result).isNull();
    }

    @Test
    void getConnectionOrNull_returnsNullWhenNoConnection() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.empty());

        GithubConnectionResponse result = service.getConnectionOrNull(1L);

        assertThat(result).isNull();
    }

    // ─────────────────────────────────────────────────
    // createOrUpdateConnection
    // ─────────────────────────────────────────────────

    @Test
    void createOrUpdateConnection_createsNewConnectionWhenNoneExists() {
        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "new-token", "repo");
        GithubApiClient.GithubUserInfo githubUser = new GithubApiClient.GithubUserInfo(100001L, "github-user");
        GithubApiClient.GithubRepoInfo repoInfo = new GithubApiClient.GithubRepoInfo(
                200001L, "my-project", "github-user/my-project",
                "https://github.com/github-user/my-project",
                RepositoryVisibility.PUBLIC, "main", "github-user",
                Instant.now(), "Java"
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(githubApiClient.getAuthenticatedUser("new-token")).willReturn(githubUser);
        given(githubApiClient.getAuthenticatedUserRepos("new-token")).willReturn(List.of(repoInfo));
        given(connectionRepository.findByUser(user)).willReturn(Optional.empty());
        given(connectionRepository.findByGithubUserId(100001L)).willReturn(Optional.empty());
        given(connectionRepository.save(any(GithubConnection.class))).willReturn(existingConnection);
        given(repositoryRepository.findByGithubConnectionAndGithubRepoId(any(), any()))
                .willReturn(Optional.empty());

        GithubConnectionResponse result = service.createOrUpdateConnection(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.githubLogin()).isEqualTo("github-user");
        verify(connectionRepository).save(any(GithubConnection.class));
    }

    @Test
    void createOrUpdateConnection_throwsBadRequestWhenAccessTokenIsBlank() {
        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "  ", "repo");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createOrUpdateConnection(1L, request))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException se = (ServiceException) ex;
                    assertThat(se.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(se.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void createOrUpdateConnection_throwsConflictWhenGithubAccountBelongsToAnotherUser() {
        User otherUser = User.builder()
                .email("other@example.com")
                .displayName("Other User")
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(otherUser, "id", 2L);

        GithubConnection connectionOwnedByOther = GithubConnection.builder()
                .user(otherUser)
                .githubUserId(100001L)
                .githubLogin("github-user")
                .accessToken("other-token")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(connectionOwnedByOther, "id", 20L);

        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "new-token", "repo");
        GithubApiClient.GithubUserInfo githubUser = new GithubApiClient.GithubUserInfo(100001L, "github-user");

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(githubApiClient.getAuthenticatedUser("new-token")).willReturn(githubUser);
        given(githubApiClient.getAuthenticatedUserRepos("new-token")).willReturn(List.of());
        given(connectionRepository.findByUser(user)).willReturn(Optional.empty());
        given(connectionRepository.findByGithubUserId(100001L)).willReturn(Optional.of(connectionOwnedByOther));

        assertThatThrownBy(() -> service.createOrUpdateConnection(1L, request))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException se = (ServiceException) ex;
                    assertThat(se.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    // ─────────────────────────────────────────────────
    // refreshFromStoredConnection
    // ─────────────────────────────────────────────────

    @Test
    void refreshFromStoredConnection_throwsForbiddenWhenNoAccessToken() {
        GithubConnection connectionWithoutToken = GithubConnection.builder()
                .user(user)
                .githubUserId(100001L)
                .githubLogin("github-user")
                .accessToken(null)
                .syncStatus(GithubSyncStatus.PENDING)
                .connectedAt(Instant.now())
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(connectionRepository.findByUser(user)).willReturn(Optional.of(connectionWithoutToken));

        assertThatThrownBy(() -> service.refreshFromStoredConnection(1L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException se = (ServiceException) ex;
                    assertThat(se.getErrorCode()).isEqualTo(ErrorCode.GITHUB_SCOPE_INSUFFICIENT);
                    assertThat(se.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }
}
