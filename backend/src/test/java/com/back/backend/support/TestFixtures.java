package com.back.backend.support;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationLengthOption;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.entity.ApplicationSourceRepository;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.entity.ApplicationToneOption;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentRepository;
import com.back.backend.domain.application.repository.ApplicationSourceRepositoryBindingRepository;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.github.entity.GithubCommit;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.github.entity.RepositoryVisibility;
import com.back.backend.domain.github.repository.GithubCommitRepository;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewAnswerTag;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.interview.repository.InterviewAnswerTagRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 통합 테스트용 엔티티 팩토리.
 *
 * 각 테스트의 @BeforeEach에서 필요한 메서드를 호출해 데이터를 생성한다.
 * 테스트에 @Transactional을 붙이면 테스트 종료 시 자동 롤백되어 격리가 보장된다.
 */
@Component
@Profile("test")
public class TestFixtures {

    // 병렬 테스트 실행 시에도 고유성을 보장하기 위해 AtomicLong 사용
    private final AtomicLong githubUserIdSeq = new AtomicLong(100_000L);
    private final AtomicLong githubRepoIdSeq = new AtomicLong(200_000L);

    private final UserRepository userRepository;
    private final GithubConnectionRepository connectionRepository;
    private final GithubRepositoryRepository repositoryRepository;
    private final GithubCommitRepository commitRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicationSourceDocumentRepository applicationSourceDocumentRepository;
    private final ApplicationSourceRepositoryBindingRepository applicationSourceRepositoryBindingRepository;
    private final DocumentRepository documentRepository;
    private final InterviewQuestionSetRepository questionSetRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewAnswerTagRepository answerTagRepository;
    private final FeedbackTagRepository feedbackTagRepository;

    public TestFixtures(UserRepository userRepository,
                        GithubConnectionRepository connectionRepository,
                        GithubRepositoryRepository repositoryRepository,
                        GithubCommitRepository commitRepository,
                        ApplicationRepository applicationRepository,
                        ApplicationQuestionRepository applicationQuestionRepository,
                        ApplicationSourceDocumentRepository applicationSourceDocumentRepository,
                        ApplicationSourceRepositoryBindingRepository applicationSourceRepositoryBindingRepository,
                        DocumentRepository documentRepository,
                        InterviewQuestionSetRepository questionSetRepository,
                        InterviewQuestionRepository questionRepository,
                        InterviewSessionRepository sessionRepository,
                        InterviewAnswerRepository answerRepository,
                        InterviewAnswerTagRepository answerTagRepository,
                        FeedbackTagRepository feedbackTagRepository) {
        this.userRepository = userRepository;
        this.connectionRepository = connectionRepository;
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.applicationRepository = applicationRepository;
        this.applicationQuestionRepository = applicationQuestionRepository;
        this.applicationSourceDocumentRepository = applicationSourceDocumentRepository;
        this.applicationSourceRepositoryBindingRepository = applicationSourceRepositoryBindingRepository;
        this.documentRepository = documentRepository;
        this.questionSetRepository = questionSetRepository;
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.answerTagRepository = answerTagRepository;
        this.feedbackTagRepository = feedbackTagRepository;
    }

    // ─────────────────────────────────────────────────
    // User
    // ─────────────────────────────────────────────────

    public User createUser(String email, String displayName) {
        return userRepository.save(User.builder()
                .email(email)
                .displayName(displayName)
                .profileImageUrl("https://example.com/profile.png")
                .status(UserStatus.ACTIVE)
                .build());
    }

    // ─────────────────────────────────────────────────
    // GithubConnection
    // ─────────────────────────────────────────────────

    /** 자동 생성된 고유 githubUserId와 기본 login을 사용한다. */
    public GithubConnection createConnection(User user) {
        return createConnection(user, githubUserIdSeq.incrementAndGet(), "github-user", "test-token");
    }

    /** login을 지정할 때 사용한다. githubUserId는 자동 생성. */
    public GithubConnection createConnection(User user, String githubLogin) {
        return createConnection(user, githubUserIdSeq.incrementAndGet(), githubLogin, "test-token");
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
        return repositoryRepository.save(GithubRepository.builder()
                .githubConnection(connection)
                .githubRepoId(githubRepoIdSeq.incrementAndGet())
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

    // ─────────────────────────────────────────────────
    // Application
    // ─────────────────────────────────────────────────

    public Application createApplication(User user, String title) {
        return createApplication(user, title, ApplicationStatus.DRAFT);
    }

    public Application createApplication(User user, String title, ApplicationStatus status) {
        return applicationRepository.save(Application.builder()
                .user(user)
                .applicationTitle(title)
                .companyName(title + "-company")
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(status)
                .build());
    }

    // ─────────────────────────────────────────────────
    // ApplicationQuestion
    // ─────────────────────────────────────────────────

    public ApplicationQuestion createApplicationQuestion(Application application, int order, String questionText) {
        return applicationQuestionRepository.save(ApplicationQuestion.builder()
                .application(application)
                .questionOrder(order)
                .questionText(questionText)
                .build());
    }

    public ApplicationQuestion createApplicationQuestion(Application application, int order,
                                                          String questionText, String generatedAnswer) {
        return applicationQuestionRepository.save(ApplicationQuestion.builder()
                .application(application)
                .questionOrder(order)
                .questionText(questionText)
                .generatedAnswer(generatedAnswer)
                .build());
    }

    public ApplicationQuestion createApplicationQuestion(Application application, int order, String questionText,
                                                          String generatedAnswer, ApplicationToneOption toneOption,
                                                          ApplicationLengthOption lengthOption, String emphasisPoint) {
        return applicationQuestionRepository.save(ApplicationQuestion.builder()
                .application(application)
                .questionOrder(order)
                .questionText(questionText)
                .generatedAnswer(generatedAnswer)
                .toneOption(toneOption)
                .lengthOption(lengthOption)
                .emphasisPoint(emphasisPoint)
                .build());
    }

    // ─────────────────────────────────────────────────
    // Document
    // ─────────────────────────────────────────────────

    public Document createResumeDocument(User user, String extractedText) {
        return documentRepository.save(Document.builder()
                .user(user)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .storagePath("uploads/test-resume.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .extractStatus(DocumentExtractStatus.SUCCESS)
                .extractedText(extractedText)
                .uploadedAt(Instant.now())
                .extractedAt(Instant.now())
                .build());
    }

    public Document createDocument(User user, DocumentType type, String extractedText) {
        return documentRepository.save(Document.builder()
                .user(user)
                .documentType(type)
                .originalFileName(type.name().toLowerCase() + ".pdf")
                .storagePath("uploads/test-" + type.name().toLowerCase() + ".pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .extractStatus(DocumentExtractStatus.SUCCESS)
                .extractedText(extractedText)
                .uploadedAt(Instant.now())
                .extractedAt(Instant.now())
                .build());
    }

    public ApplicationSourceDocument bindDocumentToApplication(Application application, Document document) {
        return applicationSourceDocumentRepository.save(ApplicationSourceDocument.builder()
                .application(application)
                .document(document)
                .build());
    }

    public ApplicationSourceRepository bindRepositoryToApplication(Application application, GithubRepository repository) {
        return applicationSourceRepositoryBindingRepository.save(ApplicationSourceRepository.builder()
                .application(application)
                .repository(repository)
                .build());
    }

    // ─────────────────────────────────────────────────
    // InterviewQuestionSet
    // ─────────────────────────────────────────────────

    public InterviewQuestionSet createQuestionSet(User user, Application application, int questionCount) {
        return questionSetRepository.save(InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title("질문 세트")
                .questionCount(questionCount)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"behavioral", "project"})
                .build());
    }

    public InterviewQuestionSet createQuestionSet(User user, Application application,
                                                   String title, int questionCount) {
        return questionSetRepository.save(InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title(title)
                .questionCount(questionCount)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"behavioral", "project"})
                .build());
    }

    // ─────────────────────────────────────────────────
    // InterviewQuestion
    // ─────────────────────────────────────────────────

    public InterviewQuestion createInterviewQuestion(InterviewQuestionSet questionSet,
                                                      int order, String questionText) {
        return questionRepository.save(InterviewQuestion.builder()
                .questionSet(questionSet)
                .questionOrder(order)
                .questionType(InterviewQuestionType.PROJECT)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionText(questionText)
                .build());
    }

    // ─────────────────────────────────────────────────
    // InterviewSession
    // ─────────────────────────────────────────────────

    public InterviewSession createInterviewSession(User user, InterviewQuestionSet questionSet,
                                                    InterviewSessionStatus status) {
        Instant now = Instant.now();
        return sessionRepository.save(InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(now)
                .lastActivityAt(now)
                .endedAt(null)
                .build());
    }

    /** startedAt과 lastActivityAt을 고정값으로 지정해야 하는 테스트 전용 (자동 일시정지 경계 검증 등). */
    public InterviewSession createInterviewSession(User user, InterviewQuestionSet questionSet,
                                                    InterviewSessionStatus status, Instant startedAt) {
        return sessionRepository.save(InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(startedAt)
                .lastActivityAt(startedAt)
                .endedAt(null)
                .build());
    }

    /** totalScore/summaryFeedback 포함, startedAt/endedAt 고정 — 목록·결과 조회 테스트용. */
    public InterviewSession createInterviewSession(User user, InterviewQuestionSet questionSet,
                                                    InterviewSessionStatus status,
                                                    Integer totalScore, String summaryFeedback,
                                                    Instant startedAt, Instant endedAt) {
        return sessionRepository.save(InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .totalScore(totalScore)
                .summaryFeedback(summaryFeedback)
                .startedAt(startedAt)
                .lastActivityAt(startedAt)
                .endedAt(endedAt)
                .build());
    }

    /** COMPLETED/FEEDBACK_COMPLETED처럼 종료 시각이 필요한 세션 — 상세 조회 테스트용. */
    public InterviewSession createTerminalSession(User user, InterviewQuestionSet questionSet,
                                                   InterviewSessionStatus status,
                                                   Instant startedAt, Instant endedAt) {
        return sessionRepository.save(InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(startedAt)
                .lastActivityAt(endedAt)
                .endedAt(endedAt)
                .build());
    }

    // ─────────────────────────────────────────────────
    // InterviewAnswer
    // ─────────────────────────────────────────────────

    public InterviewAnswer createInterviewAnswer(InterviewSession session, InterviewQuestion question,
                                                  int answerOrder, String answerText) {
        return answerRepository.save(InterviewAnswer.builder()
                .session(session)
                .question(question)
                .answerOrder(answerOrder)
                .answerText(answerText)
                .skipped(false)
                .build());
    }

    public InterviewAnswer createSkippedAnswer(InterviewSession session, InterviewQuestion question,
                                                int answerOrder) {
        return answerRepository.save(InterviewAnswer.builder()
                .session(session)
                .question(question)
                .answerOrder(answerOrder)
                .answerText(null)
                .skipped(true)
                .build());
    }

    /** score와 evaluationRationale까지 포함된 평가 완료 답변 — 결과 조회 테스트용. */
    public InterviewAnswer createEvaluatedAnswer(InterviewSession session, InterviewQuestion question,
                                                  int answerOrder, String answerText,
                                                  int score, String evaluationRationale) {
        return answerRepository.save(InterviewAnswer.builder()
                .session(session)
                .question(question)
                .answerOrder(answerOrder)
                .answerText(answerText)
                .skipped(false)
                .score(score)
                .evaluationRationale(evaluationRationale)
                .build());
    }

    // ─────────────────────────────────────────────────
    // FeedbackTag / InterviewAnswerTag
    // ─────────────────────────────────────────────────

    /** migration으로 시드된 FeedbackTag를 tagName으로 조회한다. */
    public FeedbackTag findFeedbackTag(String tagName) {
        return feedbackTagRepository.findAllByTagNameIn(java.util.List.of(tagName))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("FeedbackTag not found: " + tagName));
    }

    public InterviewAnswerTag createAnswerTag(InterviewAnswer answer, FeedbackTag tag) {
        return answerTagRepository.save(InterviewAnswerTag.builder()
                .answer(answer)
                .tag(tag)
                .build());
    }
}
