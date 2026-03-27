package com.back.backend.support;

import com.back.backend.domain.github.entity.GithubCommit;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.github.entity.RepositoryVisibility;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 통합 테스트용 엔티티 팩토리.
 *
 * 각 테스트의 @BeforeEach에서 필요한 메서드를 호출해 데이터를 생성한다.
 * 테스트에 @Transactional을 붙이면 테스트 종료 시 자동 롤백되어 격리가 보장된다.
 */
@Component
@Profile("test")
public class TestFixtures {

    private final UserRepository userRepository;
    private final GithubConnectionRepository connectionRepository;
    private final GithubRepositoryRepository repositoryRepository;
    private final GithubCommitRepository commitRepository;

    public TestFixtures(UserRepository userRepository,
                        GithubConnectionRepository connectionRepository,
                        GithubRepositoryRepository repositoryRepository,
                        GithubCommitRepository commitRepository) {
        this.userRepository = userRepository;
        this.connectionRepository = connectionRepository;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
    }

    // ─────────────────────────────────────────────────
    // User
    // ─────────────────────────────────────────────────

    public User createUser(String email, String displayName) {
        return userRepository.save(User.builder()
                .email(email)
                .displayName(displayName)
                .status(UserStatus.ACTIVE)
                .build());
    }

    // ─────────────────────────────────────────────────
    // GithubConnection
    // ─────────────────────────────────────────────────

    public GithubConnection createConnection(User user) {
        return createConnection(user, 100001L, "github-user", "test-token");
    }

    public GithubConnection createConnection(User user, Long githubUserId, String githubLogin, String accessToken) {
        return connectionRepository.save(GithubConnection.builder()
                .user(user)
                .githubUserId(githubUserId)
                .githubLogin(githubLogin)
                .accessToken(accessToken)
                .accessScope("repo,user:email")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(Instant.now())
                .lastSyncedAt(Instant.now())
                .build());
    }

    // ─────────────────────────────────────────────────
    // GithubRepository
    // ─────────────────────────────────────────────────

    public GithubRepository createRepo(GithubConnection connection, String repoName, boolean selected) {
        return createRepo(connection, repoName, selected, RepositoryVisibility.PUBLIC);
    }

    public GithubRepository createRepo(GithubConnection connection, String repoName,
                                        boolean selected, RepositoryVisibility visibility) {
        long githubRepoId = Math.abs((connection.getId() + repoName).hashCode());
        return repositoryRepository.save(GithubRepository.builder()
                .githubConnection(connection)
                .githubRepoId(githubRepoId)
                .ownerLogin(connection.getGithubLogin())
                .repoName(repoName)
                .fullName(connection.getGithubLogin() + "/" + repoName)
                .htmlUrl("https://github.com/" + connection.getGithubLogin() + "/" + repoName)
                .visibility(visibility)
                .defaultBranch("main")
                .selected(selected)
                .ownerType("owner")
                .language("Java")
                .repoSizeKb(512)
                .syncedAt(Instant.now())
                .build());
    }

    // ─────────────────────────────────────────────────
    // GithubCommit
    // ─────────────────────────────────────────────────

    public GithubCommit createUserCommit(GithubRepository repo, String message) {
        return commitRepository.save(GithubCommit.builder()
                .repository(repo)
                .githubCommitSha("sha-" + Math.abs(message.hashCode()))
                .authorLogin(repo.getGithubConnection().getGithubLogin())
                .authorName("Test User")
                .authorEmail("test@test.com")
                .commitMessage(message)
                .userCommit(true)
                .committedAt(Instant.now().minusSeconds(3600))
                .build());
    }

    public GithubCommit createOtherCommit(GithubRepository repo, String message) {
        return commitRepository.save(GithubCommit.builder()
                .repository(repo)
                .githubCommitSha("sha-other-" + Math.abs(message.hashCode()))
                .authorLogin("other-dev")
                .authorName("Other Dev")
                .authorEmail("other@test.com")
                .commitMessage(message)
                .userCommit(false)
                .committedAt(Instant.now().minusSeconds(7200))
                .build());
    }
}
