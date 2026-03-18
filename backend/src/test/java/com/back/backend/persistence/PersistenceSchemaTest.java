package com.back.backend.persistence;

import com.back.backend.application.entity.Application;
import com.back.backend.application.entity.ApplicationStatus;
import com.back.backend.github.entity.GithubConnection;
import com.back.backend.github.entity.GithubSyncStatus;
import com.back.backend.interview.entity.DifficultyLevel;
import com.back.backend.interview.entity.InterviewAnswer;
import com.back.backend.interview.entity.InterviewQuestion;
import com.back.backend.interview.entity.InterviewQuestionSet;
import com.back.backend.interview.entity.InterviewQuestionType;
import com.back.backend.interview.entity.InterviewSession;
import com.back.backend.interview.entity.InterviewSessionStatus;
import com.back.backend.user.entity.User;
import com.back.backend.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceSchemaTest {

    private static final Instant NOW = Instant.parse("2026-03-18T00:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsApplicationTypeAndQuestionTypes() {
        User user = persistUser("alpha@example.com");
        Application application = Application.builder()
                .user(user)
                .applicationTitle("네이버 백엔드 신입 지원")
                .companyName("네이버")
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(ApplicationStatus.READY)
                .build();
        entityManager.persist(application);

        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title("백엔드 예상 질문 세트")
                .questionCount(2)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"experience", "project"})
                .build();
        entityManager.persist(questionSet);
        entityManager.flush();
        entityManager.clear();

        Application reloadedApplication = entityManager.find(Application.class, application.getId());
        InterviewQuestionSet reloadedQuestionSet = entityManager.find(InterviewQuestionSet.class, questionSet.getId());

        assertThat(reloadedApplication.getApplicationType()).isEqualTo("신입");
        assertThat(reloadedQuestionSet.getQuestionTypes()).containsExactly("experience", "project");
    }

    @Test
    void rejectsDuplicateGithubConnectionPerUser() {
        User user = persistUser("github-owner@example.com");
        entityManager.persist(GithubConnection.builder()
                .user(user)
                .githubUserId(1001L)
                .githubLogin("owner-one")
                .accessScope("repo")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(NOW)
                .lastSyncedAt(NOW)
                .build());
        assertThatThrownBy(() -> entityManager.persist(GithubConnection.builder()
                .user(user)
                .githubUserId(1002L)
                .githubLogin("owner-two")
                .accessScope("repo")
                .syncStatus(GithubSyncStatus.PENDING)
                .connectedAt(NOW)
                .build()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsDuplicateEmailWhenNotNull() {
        entityManager.persist(User.builder()
                .email("dup@example.com")
                .displayName("first")
                .status(UserStatus.ACTIVE)
                .build());
        assertThatThrownBy(() -> entityManager.persist(User.builder()
                .email("dup@example.com")
                .displayName("second")
                .status(UserStatus.ACTIVE)
                .build()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void requiresAnswerTextWhenNotSkipped() {
        User user = persistUser("session-user@example.com");
        Application application = Application.builder()
                .user(user)
                .applicationTitle("카카오 백엔드 지원")
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(ApplicationStatus.READY)
                .build();
        entityManager.persist(application);

        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title("기본 질문 세트")
                .questionCount(1)
                .difficultyLevel(DifficultyLevel.EASY)
                .questionTypes(new String[]{"project"})
                .build();
        entityManager.persist(questionSet);

        InterviewQuestion question = InterviewQuestion.builder()
                .questionSet(questionSet)
                .questionOrder(1)
                .questionType(InterviewQuestionType.PROJECT)
                .difficultyLevel(DifficultyLevel.EASY)
                .questionText("프로젝트 경험을 설명해주세요.")
                .build();
        entityManager.persist(question);

        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .startedAt(NOW)
                .build();
        entityManager.persist(session);
        assertThatThrownBy(() -> entityManager.persist(InterviewAnswer.builder()
                .session(session)
                .question(question)
                .answerOrder(1)
                .answerText(null)
                .skipped(false)
                .build()))
                .isInstanceOf(PersistenceException.class);
    }

    private User persistUser(String email) {
        User user = User.builder()
                .email(email)
                .displayName("tester")
                .profileImageUrl("https://example.com/profile.png")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        return user;
    }
}
