package com.back.backend.persistence;


import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.interview.entity.*;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.support.IntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class PersistenceSchemaTest {

    private static final Instant NOW = Instant.parse("2026-03-18T00:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsFlywayV1AndSeedFeedbackTags() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success = true",
                Integer.class
        );
        assertThat(appliedMigrations).isEqualTo(1);

        Long seedCount = jdbcTemplate.queryForObject("select count(*) from feedback_tags", Long.class);
        assertThat(seedCount).isEqualTo(10L);

        FeedbackTag feedbackTag = entityManager.createQuery(
                        "select ft from FeedbackTag ft where ft.tagName = :tagName",
                        FeedbackTag.class
                )
                .setParameter("tagName", "질문 의도 미충족")
                .getSingleResult();

        assertThat(feedbackTag.getTagCategory().getValue()).isEqualTo("content");
        assertThat(feedbackTag.getDescription()).contains("핵심 의도");
    }

    @Test
    void persistsEnumTextArrayAndNullableMappings() {
        User user = persistUser(null, "nullable-user");
        Application application = Application.builder()
                .user(user)
                .applicationTitle("네이버 백엔드 신입 지원")
                .companyName(null)
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(ApplicationStatus.READY)
                .build();
        entityManager.persist(application);

        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title(null)
                .questionCount(2)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"experience", "project"})
                .build();
        entityManager.persist(questionSet);
        flushAndClear();

        Application reloadedApplication = entityManager.find(Application.class, application.getId());
        InterviewQuestionSet reloadedQuestionSet = entityManager.find(InterviewQuestionSet.class, questionSet.getId());
        User reloadedUser = entityManager.find(User.class, user.getId());

        assertThat(reloadedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloadedUser.getEmail()).isNull();
        assertThat(reloadedApplication.getApplicationType()).isEqualTo("신입");
        assertThat(reloadedApplication.getCompanyName()).isNull();
        assertThat(reloadedQuestionSet.getTitle()).isNull();
        assertThat(reloadedQuestionSet.getDifficultyLevel()).isEqualTo(DifficultyLevel.MEDIUM);
        assertThat(reloadedQuestionSet.getQuestionTypes()).containsExactly("experience", "project");
    }

    @Test
    void rejectsDuplicateGithubConnectionPerUser() {
        User user = persistUser("github-owner@example.com", "github-owner");
        entityManager.persist(GithubConnection.builder()
                .user(user)
                .githubUserId(1001L)
                .githubLogin("owner-one")
                .accessScope("repo")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(NOW)
                .lastSyncedAt(NOW)
                .build());
        flushAndClear();

        User reloadedUser = entityManager.find(User.class, user.getId());
        assertThatThrownBy(() -> {
            entityManager.persist(GithubConnection.builder()
                    .user(reloadedUser)
                    .githubUserId(1002L)
                    .githubLogin("owner-two")
                    .accessScope("repo")
                    .syncStatus(GithubSyncStatus.PENDING)
                    .connectedAt(NOW)
                    .build());
            entityManager.flush();
        })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void enforcesPartialUniqueIndexForNonNullUserEmail() {
        String indexDefinition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where schemaname = current_schema() and indexname = 'ux_users_email_not_null'",
                String.class
        );
        assertThat(indexDefinition)
                .isNotNull()
                .matches(definition -> definition.toLowerCase(Locale.ROOT).contains("where (email is not null)"));

        entityManager.persist(User.builder()
                .email("dup@example.com")
                .displayName("first")
                .status(UserStatus.ACTIVE)
                .build());
        flushAndClear();

        assertThatThrownBy(() -> {
            entityManager.persist(User.builder()
                    .email("dup@example.com")
                    .displayName("second")
                    .status(UserStatus.ACTIVE)
                    .build());
            entityManager.flush();
        })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsInvalidCheckConstraintValues() {
        User user = persistUser("question-count@example.com", "question-count");
        Application application = persistApplication(user, "질문 수 체크");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 1, DifficultyLevel.EASY, new String[]{"project"});
        InterviewQuestion question = persistQuestion(questionSet, 1);
        InterviewSession session = persistSession(user, questionSet);

        assertThatThrownBy(() -> {
            entityManager.persist(InterviewAnswer.builder()
                    .session(session)
                    .question(question)
                    .answerOrder(1)
                    .answerText(null)
                    .skipped(false)
                    .build());
            entityManager.flush();
        })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void cascadesSessionDeletionToAnswersAndAnswerTags() {
        User user = persistUser("cascade@example.com", "cascade-user");
        Application application = persistApplication(user, "세션 삭제 cascade");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 1, DifficultyLevel.EASY, new String[]{"project"});
        InterviewQuestion question = persistQuestion(questionSet, 1);
        InterviewSession session = persistSession(user, questionSet);
        InterviewAnswer answer = InterviewAnswer.builder()
                .session(session)
                .question(question)
                .answerOrder(1)
                .answerText("충분히 긴 답변입니다.")
                .skipped(false)
                .build();
        entityManager.persist(answer);

        FeedbackTag seedTag = entityManager.createQuery(
                        "select ft from FeedbackTag ft where ft.tagName = :tagName",
                        FeedbackTag.class
                )
                .setParameter("tagName", "질문 의도 미충족")
                .getSingleResult();
        entityManager.persist(InterviewAnswerTag.builder()
                .answer(answer)
                .tag(seedTag)
                .build());
        flushAndClear();

        InterviewSession managedSession = entityManager.find(InterviewSession.class, session.getId());
        entityManager.remove(managedSession);
        entityManager.flush();

        assertThat(countRows("interview_answers", "session_id", session.getId())).isZero();
        assertThat(countRows("interview_answer_tags", "answer_id", answer.getId())).isZero();
    }

    @Test
    void restrictsDeletingDocumentThatIsStillReferenced() {
        User user = persistUser("document-owner@example.com", "document-owner");
        Application application = persistApplication(user, "문서 참조 restrict");
        Document document = Document.builder()
                .user(user)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.md")
                .storagePath("/documents/resume.md")
                .mimeType("text/markdown")
                .fileSizeBytes(128L)
                .extractStatus(DocumentExtractStatus.SUCCESS)
                .uploadedAt(NOW)
                .extractedAt(NOW)
                .extractedText("이력서 본문")
                .build();
        entityManager.persist(document);
        entityManager.persist(ApplicationSourceDocument.builder()
                .application(application)
                .document(document)
                .build());
        flushAndClear();

        Document managedDocument = entityManager.find(Document.class, document.getId());
        assertThatThrownBy(() -> {
            entityManager.remove(managedDocument);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private User persistUser(String email, String displayName) {
        User user = User.builder()
                .email(email)
                .displayName(displayName)
                .profileImageUrl("https://example.com/profile.png")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Application persistApplication(User user, String title) {
        Application application = Application.builder()
                .user(user)
                .applicationTitle(title)
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(ApplicationStatus.READY)
                .build();
        entityManager.persist(application);
        return application;
    }

    private InterviewQuestionSet persistQuestionSet(
            User user,
            Application application,
            int questionCount,
            DifficultyLevel difficultyLevel,
            String[] questionTypes
    ) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title("기본 질문 세트")
                .questionCount(questionCount)
                .difficultyLevel(difficultyLevel)
                .questionTypes(questionTypes)
                .build();
        entityManager.persist(questionSet);
        return questionSet;
    }

    private InterviewQuestion persistQuestion(InterviewQuestionSet questionSet, int questionOrder) {
        InterviewQuestion question = InterviewQuestion.builder()
                .questionSet(questionSet)
                .questionOrder(questionOrder)
                .questionType(InterviewQuestionType.PROJECT)
                .difficultyLevel(DifficultyLevel.EASY)
                .questionText("프로젝트 경험을 설명해주세요.")
                .build();
        entityManager.persist(question);
        return question;
    }

    private InterviewSession persistSession(User user, InterviewQuestionSet questionSet) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .startedAt(NOW)
                .build();
        entityManager.persist(session);
        return session;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private long countRows(String tableName, String columnName, Long id) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Long.class,
                id
        );
        return count == null ? 0L : count;
    }
}
