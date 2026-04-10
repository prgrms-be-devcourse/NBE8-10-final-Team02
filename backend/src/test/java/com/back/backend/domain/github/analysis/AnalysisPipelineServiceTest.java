package com.back.backend.domain.github.analysis;

import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.github.entity.NodeType;
import com.back.backend.domain.github.entity.RepositoryVisibility;
import com.back.backend.domain.github.portfolio.BatchRepoSummaryGeneratorService;
import com.back.backend.domain.github.portfolio.MergedSummaryService;
import com.back.backend.domain.github.portfolio.RepoSummaryGeneratorService;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.github.service.GithubSyncService;
import com.back.backend.domain.portfolio.service.FailedJobRedisStore;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.security.GitleaksService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisPipelineServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GithubRepositoryRepository repositoryRepository;

    @Mock
    private GithubCommitRepository commitRepository;

    @Mock
    private SyncStatusService syncStatusService;

    @Mock
    private SignificanceCheckService significanceCheckService;

    @Mock
    private RepoCloneService repoCloneService;

    @Mock
    private StaticAnalysisService staticAnalysisService;

    @Mock
    private CallGraphService callGraphService;

    @Mock
    private CodeIndexService codeIndexService;

    @Mock
    private ContributionExtractorService contributionExtractorService;

    @Mock
    private RepoSummaryGeneratorService repoSummaryGeneratorService;

    @Mock
    private BatchRepoSummaryGeneratorService batchRepoSummaryGeneratorService;

    @Mock
    private MergedSummaryService mergedSummaryService;

    @Mock
    private RepoFileFilterService repoFileFilterService;

    @Mock
    private GitleaksService gitleaksService;

    @Mock
    private FailedJobRedisStore failedJobRedisStore;

    @Mock
    private GithubSyncService githubSyncService;

    private AnalysisPipelineService service;
    private AnalysisPipelineService asyncProxy;
    private User user;
    private GithubRepository repo;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        service = spy(new AnalysisPipelineService(
                userRepository,
                repositoryRepository,
                commitRepository,
                syncStatusService,
                significanceCheckService,
                repoCloneService,
                staticAnalysisService,
                callGraphService,
                codeIndexService,
                contributionExtractorService,
                repoSummaryGeneratorService,
                batchRepoSummaryGeneratorService,
                mergedSummaryService,
                directExecutor,
                repoFileFilterService,
                gitleaksService,
                new ObjectMapper(),
                failedJobRedisStore,
                githubSyncService
        ));
        asyncProxy = mock(AnalysisPipelineService.class);
        ReflectionTestUtils.setField(service, "self", asyncProxy);
        doNothing().when(asyncProxy).executeAsync(anyLong(), anyLong());
        doNothing().when(asyncProxy).executeBatchAsync(anyLong(), anyList());

        user = user(1L, "octocat");
        repo = repository(100L, user, "octocat", "portfolio");
    }

    @Test
    void triggerAnalysis_rejectsDuplicateInProgressRequest() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(repositoryRepository.findById(100L)).willReturn(Optional.of(repo));
        given(syncStatusService.getStatus(1L, 100L)).willReturn(Optional.of(
                new SyncStatusService.SyncStatusData(
                        100L, SyncStatus.PENDING, null, null, Instant.now().plusSeconds(60), null, null, null
                )));

        assertThatThrownBy(() -> service.triggerAnalysis(1L, 100L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException error = (ServiceException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(syncStatusService, never()).setPending(anyLong(), anyLong(), any());
        verify(asyncProxy, never()).executeAsync(anyLong(), anyLong());
    }

    @Test
    void triggerAnalysis_rejectsForeignRepositoryAccess() {
        User otherUser = user(2L, "other-user");
        GithubRepository foreignRepo = repository(200L, otherUser, "other-user", "private-repo");

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(repositoryRepository.findById(200L)).willReturn(Optional.of(foreignRepo));

        assertThatThrownBy(() -> service.triggerAnalysis(1L, 200L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException error = (ServiceException) ex;
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void runStaticAnalysisSteps_autoSyncsCommitsWhenFirstCheckSaysNoCommits() throws Exception {
        Path repoPath = Files.createTempDirectory("analysis-auto-sync");
        Path sourceFile = Files.createDirectories(repoPath.resolve("src/main/java")).resolve("App.java");
        Files.writeString(sourceFile, "class App {}");

        given(significanceCheckService.isSignificant(repo, 1L))
                .willReturn(Optional.of("커밋이 없습니다. 먼저 동기화가 필요합니다."))
                .willReturn(Optional.empty());
        given(repoCloneService.cloneOrFetch(repo.getHtmlUrl(), 1L, 100L)).willReturn(repoPath);
        given(repoCloneService.getRepoPath(1L, 100L)).willReturn(repoPath);
        given(repoFileFilterService.filter(repoPath))
                .willReturn(new RepoFileFilterService.FilterResult(List.of(sourceFile), List.of()));
        given(gitleaksService.scanRepo(repoPath)).willReturn(GitleaksService.GitleaksScanResult.empty());
        given(commitRepository.findDistinctAuthorEmailsByRepositoryAndUserCommitTrue(repo))
                .willReturn(List.of("octocat@example.com"));
        given(contributionExtractorService.getAuthoredFiles(repoPath, "octocat@example.com"))
                .willReturn(Set.of("src/main/java/App.java"));
        given(staticAnalysisService.analyze(repoPath, Optional.of(Set.of("src/main/java/App.java"))))
                .willReturn(List.of(node("src/main/java/App.java")));
        given(callGraphService.computePageRank(anyList())).willReturn(Map.of("com.example.App", 1.0));

        boolean passed = service.runStaticAnalysisSteps(user, repo, 1L, 100L);

        assertThat(passed).isTrue();
        verify(githubSyncService).syncCommits(1L, 100L);
        verify(significanceCheckService, org.mockito.Mockito.times(2)).isSignificant(repo, 1L);
        verify(syncStatusService).setInProgress(1L, 100L, "clone");
        verify(syncStatusService).setInProgress(1L, 100L, "analysis");
    }

    @Test
    void runStaticAnalysisSteps_skipsAndMarksAnalyzedWhenNotSignificant() {
        given(significanceCheckService.isSignificant(repo, 1L))
                .willReturn(Optional.of("최근 변경량이 기준치에 미달합니다."));

        boolean passed = service.runStaticAnalysisSteps(user, repo, 1L, 100L);

        assertThat(passed).isFalse();
        verify(syncStatusService).setSkipped(1L, 100L, "최근 변경량이 기준치에 미달합니다.");
        verify(significanceCheckService).markAnalyzed(1L, 100L);
        verify(githubSyncService, never()).syncCommits(anyLong(), anyLong());
        verify(repoCloneService, never()).cloneOrFetch(anyString(), anyLong(), anyLong());
    }

    @Test
    void runStaticAnalysisSteps_largeRepoUsesIntersectionOfAuthoredAndSafeFiles() throws Exception {
        Path repoPath = Files.createTempDirectory("analysis-large");
        Path srcDir = Files.createDirectories(repoPath.resolve("src/main/java"));
        Path app1 = srcDir.resolve("App1.java");
        Path app2 = srcDir.resolve("App2.java");
        Path app3 = srcDir.resolve("App3.java");
        Files.writeString(app1, "class App1 {}");
        Files.writeString(app2, "class App2 {}");
        Files.writeString(app3, "class App3 {}");
        for (int i = 0; i < 301; i++) {
            Files.writeString(srcDir.resolve("Generated" + i + ".java"), "class Generated" + i + " {}");
        }

        given(significanceCheckService.isSignificant(repo, 1L)).willReturn(Optional.empty());
        given(repoCloneService.cloneOrFetch(repo.getHtmlUrl(), 1L, 100L)).willReturn(repoPath);
        given(repoCloneService.getRepoPath(1L, 100L)).willReturn(repoPath);
        given(repoFileFilterService.filter(repoPath))
                .willReturn(new RepoFileFilterService.FilterResult(List.of(app1, app3), List.of()));
        given(gitleaksService.scanRepo(repoPath)).willReturn(GitleaksService.GitleaksScanResult.empty());
        given(commitRepository.findDistinctAuthorEmailsByRepositoryAndUserCommitTrue(repo))
                .willReturn(List.of("octocat@example.com"));
        given(contributionExtractorService.getAuthoredFiles(repoPath, "octocat@example.com"))
                .willReturn(Set.of("src/main/java/App1.java", "src/main/java/App2.java"));
        given(staticAnalysisService.analyze(any(), any())).willReturn(List.of());
        given(callGraphService.computePageRank(anyList())).willReturn(Map.of());

        service.runStaticAnalysisSteps(user, repo, 1L, 100L);

        ArgumentCaptor<Optional<Set<String>>> scopeCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(staticAnalysisService).analyze(eq(repoPath), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue()).contains(Set.of("src/main/java/App1.java"));
    }

    @Test
    void applyFileFilterAndSecretScan_excludesSecretFilesAndPersistsSanitizedMetadata() throws Exception {
        Path repoPath = Files.createTempDirectory("analysis-secrets");
        Path srcDir = Files.createDirectories(repoPath.resolve("src"));
        Path safeFile = srcDir.resolve("App.java");
        Path secretFile = srcDir.resolve("Secret.java");
        Files.writeString(safeFile, "class App {}");
        Files.writeString(secretFile, "class Secret {}");

        given(repoFileFilterService.filter(repoPath))
                .willReturn(new RepoFileFilterService.FilterResult(List.of(safeFile, secretFile), List.of()));
        given(gitleaksService.scanRepo(repoPath)).willReturn(new GitleaksService.GitleaksScanResult(
                true,
                List.of(new GitleaksService.SecretFinding("src/Secret.java", "generic-api-key", "Hardcoded token"))
        ));
        given(repositoryRepository.save(repo)).willReturn(repo);

        @SuppressWarnings("unchecked")
        Set<String> safeFiles = (Set<String>) ReflectionTestUtils.invokeMethod(
                service, "applyFileFilterAndSecretScan", repo, repoPath);

        assertThat(safeFiles).containsExactly("src/App.java");
        assertThat(repo.getSecretExcludedFiles()).contains("src/Secret.java", "generic-api-key");
        assertThat(repo.getSecretExcludedFiles()).doesNotContain("ghp_real_secret");

        List<Map<String, String>> excluded = new ObjectMapper().readValue(
                repo.getSecretExcludedFiles(),
                new TypeReference<>() {}
        );
        assertThat(excluded).containsExactly(Map.of("filePath", "src/Secret.java", "ruleId", "generic-api-key"));
        verify(repositoryRepository).save(repo);
    }

    @Test
    void executeBatchAsync_preservesCompletedReposAndMarksRemainingFailuresRetryable() {
        GithubRepository repo1 = repository(101L, user, "octocat", "repo-one");
        GithubRepository repo2 = repository(102L, user, "octocat", "repo-two");

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(repositoryRepository.findByIdWithConnection(101L)).willReturn(Optional.of(repo1));
        given(repositoryRepository.findByIdWithConnection(102L)).willReturn(Optional.of(repo2));
        given(syncStatusService.getStatus(1L, 101L)).willReturn(Optional.of(
                new SyncStatusService.SyncStatusData(101L, SyncStatus.PENDING, null, null, null, null, null, null)));
        given(syncStatusService.getStatus(1L, 102L)).willReturn(Optional.of(
                new SyncStatusService.SyncStatusData(102L, SyncStatus.PENDING, null, null, null, null, null, null)));
        given(commitRepository.findDistinctAuthorEmailsByRepositoryAndUserCommitTrue(repo1))
                .willReturn(List.of("octocat@example.com"));
        doReturn(true).when(service).runStaticAnalysisSteps(eq(user), eq(repo1), eq(1L), eq(101L));
        doReturn(true).when(service).runStaticAnalysisSteps(eq(user), eq(repo2), eq(1L), eq(102L));
        doAnswer(invocation -> {
            Consumer<GithubRepository> onRepoDataCollected = invocation.getArgument(4);
            Consumer<List<GithubRepository>> onChunkStart = invocation.getArgument(5);
            Consumer<GithubRepository> onRepoSaved = invocation.getArgument(6);
            onRepoDataCollected.accept(repo1);
            onRepoDataCollected.accept(repo2);
            onChunkStart.accept(List.of(repo1, repo2));
            onRepoSaved.accept(repo1);
            throw new IllegalStateException("JSON 파싱 실패: unexpected EOF");
        }).when(batchRepoSummaryGeneratorService)
                .generateAll(eq(user), eq(List.of(repo1, repo2)), eq("octocat@example.com"),
                        eq("significant_commits"), any(), any(), any());

        service.executeBatchAsync(1L, List.of(101L, 102L));

        verify(syncStatusService).setCompleted(1L, 101L);
        verify(syncStatusService).setFailed(
                1L,
                102L,
                "[RETRY] AI 출력이 중간에 잘렸습니다. 재시도하면 성공할 수 있습니다."
        );
        verify(syncStatusService, never()).setFailed(eq(1L), eq(101L), anyString());
        verify(significanceCheckService).markAnalyzed(1L, 101L);
        verify(failedJobRedisStore).push(
                1L,
                FailedJobRedisStore.JobType.GITHUB_ANALYSIS,
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE.name(),
                "[RETRY] AI 출력이 중간에 잘렸습니다. 재시도하면 성공할 수 있습니다."
        );
    }

    private User user(Long id, String githubLogin) {
        User created = User.builder()
                .email(githubLogin + "@example.com")
                .displayName(githubLogin)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private GithubRepository repository(Long id, User owner, String githubLogin, String repoName) {
        GithubConnection connection = GithubConnection.builder()
                .user(owner)
                .githubUserId(id + 1000)
                .githubLogin(githubLogin)
                .accessToken("token")
                .accessScope("repo,user:email")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(connection, "id", id + 10);

        GithubRepository repository = GithubRepository.builder()
                .githubConnection(connection)
                .githubRepoId(id + 5000)
                .ownerLogin(githubLogin)
                .repoName(repoName)
                .fullName(githubLogin + "/" + repoName)
                .htmlUrl("https://github.com/" + githubLogin + "/" + repoName)
                .visibility(RepositoryVisibility.PUBLIC)
                .defaultBranch("main")
                .selected(true)
                .syncedAt(Instant.now())
                .ownerType("owner")
                .language("Java")
                .build();
        ReflectionTestUtils.setField(repository, "id", id);
        return repository;
    }

    private AnalysisNode node(String filePath) {
        return new AnalysisNode(
                "com.example.App",
                filePath,
                1,
                10,
                NodeType.CLASS,
                List.of(),
                List.of()
        );
    }
}
