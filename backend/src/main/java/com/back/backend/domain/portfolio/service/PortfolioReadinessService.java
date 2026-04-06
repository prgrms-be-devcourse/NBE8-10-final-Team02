package com.back.backend.domain.portfolio.service;

import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse;
import com.back.backend.domain.portfolio.dto.response.PortfolioReadinessResponse.AlertItem;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioReadinessService {

    private static final String CONNECTION_CONNECTED = "connected";
    private static final String CONNECTION_NOT_CONNECTED = "not_connected";

    private static final String SCOPE_NOT_APPLICABLE = "not_applicable";
    private static final String SCOPE_PUBLIC_ONLY = "public_only";
    private static final String SCOPE_PRIVATE_READY = "private_ready";
    private static final String SCOPE_INSUFFICIENT = "insufficient";

    private static final String MISSING_GITHUB_CONNECTION = "github_connection";
    private static final String MISSING_SELECTED_REPOSITORY = "selected_repository";
    private static final String MISSING_DOCUMENT_SOURCE = "document_source";
    private static final String MISSING_DOCUMENT_EXTRACT_SUCCESS = "document_extract_success";

    private static final String ACTION_CONNECT_GITHUB = "connect_github";
    private static final String ACTION_SELECT_REPOSITORY = "select_repository";
    private static final String ACTION_UPLOAD_DOCUMENT = "upload_document";
    private static final String ACTION_RETRY_DOCUMENT_EXTRACTION = "retry_document_extraction";
    private static final String ACTION_START_APPLICATION = "start_application";

    private final UserRepository userRepository;
    private final GithubConnectionRepository githubConnectionRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final DocumentRepository documentRepository;
    private final FailedJobRedisStore failedJobRedisStore;

    public PortfolioReadinessResponse getReadiness(Long userId) {
        // findByUserIdWithUser: JOIN FETCH로 connection과 user를 한 번에 조회 (trip 절감)
        GithubConnection connection = githubConnectionRepository.findByUserIdWithUser(userId).orElse(null);

        // connection이 없을 때만 user를 별도 조회 (GitHub 미연동 사용자 처리)
        User user = (connection != null)
                ? connection.getUser()
                : userRepository.findById(userId)
                        .orElseThrow(() -> new ServiceException(
                                ErrorCode.USER_NOT_FOUND,
                                HttpStatus.NOT_FOUND,
                                "사용자를 찾을 수 없습니다."
                        ));

        List<Document> documents = documentRepository.findAllByUserId(userId);

        int selectedRepositoryCount = countSelectedRepositories(connection);
        int totalDocumentCount = documents.size();
        int extractSuccessCount = countDocumentsByStatus(documents, DocumentExtractStatus.SUCCESS);
        int extractFailedCount = countDocumentsByStatus(documents, DocumentExtractStatus.FAILED);

        boolean canStartApplication = selectedRepositoryCount > 0 || extractSuccessCount > 0;
        List<String> missingItems = determineMissingItems(
                connection,
                selectedRepositoryCount,
                totalDocumentCount,
                extractSuccessCount
        );

        return new PortfolioReadinessResponse(
                PortfolioReadinessResponse.Profile.from(user),
                new PortfolioReadinessResponse.Github(
                        determineConnectionStatus(connection),
                        determineScopeStatus(connection),
                        selectedRepositoryCount,
                        PortfolioReadinessResponse.CountMetric.notReady()
                ),
                new PortfolioReadinessResponse.Documents(
                        totalDocumentCount,
                        extractSuccessCount,
                        extractFailedCount
                ),
                new PortfolioReadinessResponse.Readiness(
                        missingItems,
                        determineNextRecommendedAction(
                                canStartApplication,
                                connection,
                                selectedRepositoryCount,
                                totalDocumentCount,
                                extractSuccessCount
                        ),
                        canStartApplication
                ),
                new PortfolioReadinessResponse.Alerts(
                        buildRecentFailedJobs(userId)
                )
        );
    }

    /**
     * Redis에서 최근 실패 항목을 읽어 RecentFailedJobs를 구성한다.
     * Redis 오류 시 {@code FailedJobRedisStore.getRecent}가 빈 리스트를 반환하므로
     * 이 메서드는 항상 "ready" 상태를 반환한다.
     */
    private PortfolioReadinessResponse.RecentFailedJobs buildRecentFailedJobs(Long userId) {
        List<AlertItem> items = failedJobRedisStore.getRecent(userId);
        return new PortfolioReadinessResponse.RecentFailedJobs("ready", items.isEmpty() ? null : items);
    }

    private int countSelectedRepositories(GithubConnection connection) {
        if (connection == null) {
            return 0;
        }
        // COUNT 쿼리: 전체 레코드를 메모리에 올리지 않는다
        return githubRepositoryRepository.countByGithubConnectionAndSelectedTrue(connection);
    }

    private int countDocumentsByStatus(List<Document> documents, DocumentExtractStatus status) {
        return (int) documents.stream()
                .filter(document -> document.getExtractStatus() == status)
                .count();
    }

    private List<String> determineMissingItems(
            GithubConnection connection,
            int selectedRepositoryCount,
            int totalDocumentCount,
            int extractSuccessCount
    ) {
        List<String> missingItems = new ArrayList<>();

        if (connection == null) {
            missingItems.add(MISSING_GITHUB_CONNECTION);
        } else if (selectedRepositoryCount == 0) {
            missingItems.add(MISSING_SELECTED_REPOSITORY);
        }

        if (totalDocumentCount == 0) {
            missingItems.add(MISSING_DOCUMENT_SOURCE);
        } else if (extractSuccessCount == 0) {
            missingItems.add(MISSING_DOCUMENT_EXTRACT_SUCCESS);
        }

        return List.copyOf(missingItems);
    }

    private String determineNextRecommendedAction(
            boolean canStartApplication,
            GithubConnection connection,
            int selectedRepositoryCount,
            int totalDocumentCount,
            int extractSuccessCount
    ) {
        if (canStartApplication) {
            return ACTION_START_APPLICATION;
        }
        if (connection == null) {
            return ACTION_CONNECT_GITHUB;
        }
        if (selectedRepositoryCount == 0) {
            return ACTION_SELECT_REPOSITORY;
        }
        if (totalDocumentCount == 0) {
            return ACTION_UPLOAD_DOCUMENT;
        }
        if (extractSuccessCount == 0) {
            return ACTION_RETRY_DOCUMENT_EXTRACTION;
        }
        return ACTION_START_APPLICATION;
    }

    private String determineConnectionStatus(GithubConnection connection) {
        return connection == null ? CONNECTION_NOT_CONNECTED : CONNECTION_CONNECTED;
    }

    private String determineScopeStatus(GithubConnection connection) {
        if (connection == null) {
            return SCOPE_NOT_APPLICABLE;
        }

        String accessScope = connection.getAccessScope();
        if (accessScope == null || accessScope.isBlank()) {
            return SCOPE_PUBLIC_ONLY;
        }

        Set<String> scopes = Arrays.stream(accessScope.split("[,\\s]+"))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toSet());

        if (scopes.contains("repo")) {
            return SCOPE_PRIVATE_READY;
        }
        if (scopes.contains("public_repo")) {
            return SCOPE_PUBLIC_ONLY;
        }
        return SCOPE_INSUFFICIENT;
    }
}
