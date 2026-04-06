package com.back.backend.domain.activity.service;

import com.back.backend.domain.activity.entity.UserActivityLog;
import com.back.backend.domain.activity.entity.UserStreak;
import com.back.backend.domain.activity.event.ApplicationReadyEvent;
import com.back.backend.domain.activity.event.GithubSyncCompletedEvent;
import com.back.backend.domain.activity.event.InterviewSessionCompletedEvent;
import com.back.backend.domain.activity.repository.UserActivityLogRepository;
import com.back.backend.domain.activity.repository.UserStreakRepository;
import com.back.backend.domain.document.event.DocumentUploadedEvent;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executor;

@Service
public class ActivityTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ActivityTrackingService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserStreakRepository userStreakRepository;
    private final UserActivityLogRepository userActivityLogRepository;
    private final UserRepository userRepository;
    private final Executor activityTaskExecutor;
    private final PlatformTransactionManager transactionManager;

    public ActivityTrackingService(
            UserStreakRepository userStreakRepository,
            UserActivityLogRepository userActivityLogRepository,
            UserRepository userRepository,
            @Qualifier("activityTaskExecutor") Executor activityTaskExecutor,
            PlatformTransactionManager transactionManager
    ) {
        this.userStreakRepository = userStreakRepository;
        this.userActivityLogRepository = userActivityLogRepository;
        this.userRepository = userRepository;
        this.activityTaskExecutor = activityTaskExecutor;
        this.transactionManager = transactionManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInterviewSessionCompleted(InterviewSessionCompletedEvent event) {
        activityTaskExecutor.execute(() -> recordActivity(event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        activityTaskExecutor.execute(() -> recordActivity(event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationReady(ApplicationReadyEvent event) {
        activityTaskExecutor.execute(() -> recordActivity(event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGithubSyncCompleted(GithubSyncCompletedEvent event) {
        activityTaskExecutor.execute(() -> recordActivity(event.userId()));
    }

    void recordActivity(Long userId) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                LocalDate todayKst = LocalDate.now(KST);

                // activity log upsert
                userActivityLogRepository.findByUserIdAndActivityDate(userId, todayKst)
                        .ifPresentOrElse(
                                UserActivityLog::increment,
                                () -> {
                                    User user = userRepository.getReferenceById(userId);
                                    userActivityLogRepository.save(new UserActivityLog(user, todayKst));
                                }
                        );

                // streak update
                UserStreak streak = userStreakRepository.findByUserId(userId)
                        .orElseGet(() -> {
                            User user = userRepository.getReferenceById(userId);
                            return userStreakRepository.save(new UserStreak(user));
                        });
                streak.recordActivity(todayKst);
            });
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate activity record for userId={}, ignoring", userId);
        } catch (Exception e) {
            log.warn("Failed to record activity for userId={}", userId, e);
        }
    }
}
