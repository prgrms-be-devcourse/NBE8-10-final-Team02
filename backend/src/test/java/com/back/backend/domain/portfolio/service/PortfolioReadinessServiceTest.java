package com.back.backend.domain.portfolio.service;

import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse.AlertItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PortfolioReadinessServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-26T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private GithubConnectionRepository githubConnectionRepository;

    @Mock
    private GithubRepositoryRepository githubRepositoryRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FailedJobRedisStore failedJobRedisStore;

    @InjectMocks
    private PortfolioReadinessService portfolioReadinessService;

    @Test
    void getReadiness_returnsEmptyStateForBlankUser() {
        User user = user(1L);
        given(githubConnectionRepository.findByUserIdWithUser(1L)).willReturn(Optional.empty());
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(documentRepository.findAllByUserId(1L)).willReturn(List.of());

        PortfolioReadinessResponse result = portfolioReadinessService.getReadiness(1L);

        assertThat(result.github().connectionStatus()).isEqualTo("not_connected");
        assertThat(result.github().scopeStatus()).isEqualTo("not_applicable");
        assertThat(result.github().selectedRepositoryCount()).isZero();
        assertThat(result.documents().totalCount()).isZero();
        assertThat(result.readiness().missingItems())
                .containsExactly("github_connection", "document_source");
        assertThat(result.readiness().nextRecommendedAction()).isEqualTo("connect_github");
        assertThat(result.readiness().canStartApplication()).isFalse();
    }

    @Test
    void getReadiness_returnsGithubOnlyState() {
        User user = user(1L);
        GithubConnection connection = connection(user, null);
        given(githubConnectionRepository.findByUserIdWithUser(1L)).willReturn(Optional.of(connection));
        given(githubRepositoryRepository.countByGithubConnectionAndSelectedTrue(connection)).willReturn(2);
        given(documentRepository.findAllByUserId(1L)).willReturn(List.of());

        PortfolioReadinessResponse result = portfolioReadinessService.getReadiness(1L);

        assertThat(result.github().connectionStatus()).isEqualTo("connected");
        assertThat(result.github().scopeStatus()).isEqualTo("public_only");
        assertThat(result.github().selectedRepositoryCount()).isEqualTo(2);
        assertThat(result.readiness().missingItems()).containsExactly("document_source");
        assertThat(result.readiness().nextRecommendedAction()).isEqualTo("start_application");
        assertThat(result.readiness().canStartApplication()).isTrue();
    }

    @Test
    void getReadiness_returnsDocumentOnlyState() {
        User user = user(1L);
        given(githubConnectionRepository.findByUserIdWithUser(1L)).willReturn(Optional.empty());
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(documentRepository.findAllByUserId(1L))
                .willReturn(List.of(document(user, "resume.pdf", DocumentExtractStatus.SUCCESS)));

        PortfolioReadinessResponse result = portfolioReadinessService.getReadiness(1L);

        assertThat(result.documents().totalCount()).isEqualTo(1);
        assertThat(result.documents().extractSuccessCount()).isEqualTo(1);
        assertThat(result.documents().extractFailedCount()).isZero();
        assertThat(result.readiness().missingItems()).containsExactly("github_connection");
        assertThat(result.readiness().nextRecommendedAction()).isEqualTo("start_application");
        assertThat(result.readiness().canStartApplication()).isTrue();
    }

    @Test
    void getReadiness_returnsReadyStateWhenGithubAndDocumentsExist() {
        User user = user(1L);
        GithubConnection connection = connection(user, "read:user repo");
        given(githubConnectionRepository.findByUserIdWithUser(1L)).willReturn(Optional.of(connection));
        given(githubRepositoryRepository.countByGithubConnectionAndSelectedTrue(connection)).willReturn(1);
        given(documentRepository.findAllByUserId(1L))
                .willReturn(List.of(
                        document(user, "resume.pdf", DocumentExtractStatus.SUCCESS),
                        document(user, "scan.pdf", DocumentExtractStatus.FAILED)
                ));

        PortfolioReadinessResponse result = portfolioReadinessService.getReadiness(1L);

        assertThat(result.github().scopeStatus()).isEqualTo("private_ready");
        assertThat(result.documents().totalCount()).isEqualTo(2);
        assertThat(result.documents().extractSuccessCount()).isEqualTo(1);
        assertThat(result.documents().extractFailedCount()).isEqualTo(1);
        assertThat(result.readiness().missingItems()).isEmpty();
        assertThat(result.readiness().nextRecommendedAction()).isEqualTo("start_application");
        assertThat(result.readiness().canStartApplication()).isTrue();
    }

    @Test
    void getReadiness_returnsNotReadyPlaceholdersForDeferredFields() {
        User user = user(1L);
        given(githubConnectionRepository.findByUserIdWithUser(1L)).willReturn(Optional.empty());
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(documentRepository.findAllByUserId(1L)).willReturn(List.of());

        PortfolioReadinessResponse result = portfolioReadinessService.getReadiness(1L);

        assertThat(result.github().recentCollectedCommitCount().status()).isEqualTo("not_ready");
        assertThat(result.github().recentCollectedCommitCount().value()).isNull();
        // recentFailedJobs는 Redis를 실제로 조회하므로 항상 "ready" 상태로 반환된다
        // (FailedJobRedisStore mock은 빈 리스트를 반환 → items = null)
        assertThat(result.alerts().recentFailedJobs().status()).isEqualTo("ready");
        assertThat(result.alerts().recentFailedJobs().items()).isNull();
    }

    @Test
    void getReadiness_returnsFailedJobsFromRedis() {
        User user = user(1L);
        AlertItem alert = new AlertItem("GITHUB_COMMIT_SYNC_FAILED", "[GITHUB_SYNC] 동기화 실패", FIXED_NOW);
        given(githubConnectionRepository.findByUserIdWithUser(1L)).willReturn(Optional.empty());
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(documentRepository.findAllByUserId(1L)).willReturn(List.of());
        given(failedJobRedisStore.getRecent(1L)).willReturn(List.of(alert));

        PortfolioReadinessResponse result = portfolioReadinessService.getReadiness(1L);

        assertThat(result.alerts().recentFailedJobs().status()).isEqualTo("ready");
        assertThat(result.alerts().recentFailedJobs().items()).hasSize(1);
        assertThat(result.alerts().recentFailedJobs().items().get(0).code())
                .isEqualTo("GITHUB_COMMIT_SYNC_FAILED");
    }

    @Test
    void getReadiness_recommendsRepositorySelectionBeforeRetryingDocuments() {
        User user = user(1L);
        GithubConnection connection = connection(user, "read:user");
        given(githubConnectionRepository.findByUserIdWithUser(1L)).willReturn(Optional.of(connection));
        given(githubRepositoryRepository.countByGithubConnectionAndSelectedTrue(connection)).willReturn(0);
        given(documentRepository.findAllByUserId(1L))
                .willReturn(List.of(document(user, "scan.pdf", DocumentExtractStatus.FAILED)));

        PortfolioReadinessResponse result = portfolioReadinessService.getReadiness(1L);

        assertThat(result.github().scopeStatus()).isEqualTo("insufficient");
        assertThat(result.readiness().missingItems())
                .containsExactly("selected_repository", "document_extract_success");
        assertThat(result.readiness().nextRecommendedAction()).isEqualTo("select_repository");
        assertThat(result.readiness().canStartApplication()).isFalse();
    }

    private User user(Long id) {
        User user = User.builder()
                .email("tester@example.com")
                .displayName("tester")
                .profileImageUrl("https://example.com/avatar.png")
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private GithubConnection connection(User user, String accessScope) {
        return GithubConnection.builder()
                .user(user)
                .githubUserId(100L)
                .githubLogin("octocat")
                .accessScope(accessScope)
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(FIXED_NOW)
                .lastSyncedAt(FIXED_NOW)
                .build();
    }

    private Document document(User user, String fileName, DocumentExtractStatus extractStatus) {
        return Document.builder()
                .user(user)
                .documentType(DocumentType.RESUME)
                .originalFileName(fileName)
                .storagePath("uploads/" + fileName)
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .extractStatus(extractStatus)
                .uploadedAt(FIXED_NOW)
                .build();
    }
}
