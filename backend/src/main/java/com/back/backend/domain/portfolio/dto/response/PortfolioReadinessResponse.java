package com.back.backend.domain.portfolio.dto.response;

import com.back.backend.domain.user.entity.User;

import java.time.Instant;
import java.util.List;

public record PortfolioReadinessResponse(
        Profile profile,
        Github github,
        Documents documents,
        Readiness readiness,
        Alerts alerts
) {

    public record Profile(
            Long userId,
            String displayName,
            String email,
            String profileImageUrl
    ) {
        public static Profile from(User user) {
            return new Profile(
                    user.getId(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getProfileImageUrl()
            );
        }
    }

    public record Github(
            String connectionStatus,
            String scopeStatus,
            int selectedRepositoryCount,
            CountMetric recentCollectedCommitCount
    ) {
    }

    public record Documents(
            int totalCount,
            int extractSuccessCount,
            int extractFailedCount
    ) {
    }

    public record Readiness(
            List<String> missingItems,
            String nextRecommendedAction,
            boolean canStartApplication
    ) {
        public Readiness {
            missingItems = List.copyOf(missingItems);
        }
    }

    public record Alerts(
            RecentFailedJobs recentFailedJobs
    ) {
    }

    public record CountMetric(
            String status,
            Integer value
    ) {
        public static CountMetric notReady() {
            return new CountMetric("not_ready", null);
        }
    }

    public record RecentFailedJobs(
            String status,
            List<AlertItem> items
    ) {
        public static RecentFailedJobs notReady() {
            return new RecentFailedJobs("not_ready", null);
        }
    }

    public record AlertItem(
            String code,
            String message,
            Instant occurredAt
    ) {
    }
}
