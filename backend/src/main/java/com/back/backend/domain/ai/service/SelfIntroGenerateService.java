package com.back.backend.domain.ai.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.SelfIntroPayloadBuilder;
import com.back.backend.domain.ai.pipeline.payload.SelfIntroPayloadBuilder.QuestionInput;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationLengthOption;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.application.service.ApplicationStatusService;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @deprecated 직접 호출하지 말 것. {@link com.back.backend.domain.application.service.AsyncSelfIntroGenerateService}를 사용할 것.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class SelfIntroGenerateService {

    private static final String TEMPLATE_ID = "ai.self_intro.generate.v1";
    private static final int TOKEN_PER_SHORT  = 500;
    private static final int TOKEN_PER_MEDIUM = 800;
    private static final int TOKEN_PER_LONG   = 1200;
    private static final int TOKEN_OVERHEAD   = 300;
    private static final int TOKEN_MIN        = 2000;
    private static final int TOKEN_MAX        = 8000;

    private final ApplicationRepository applicationRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicationSourceDocumentBindingRepository sourceDocumentBindingRepository;
    private final ApplicationStatusService applicationStatusService;
    private final SelfIntroPayloadBuilder payloadBuilder;
    private final AiPipeline aiPipeline;
    private final TransactionTemplate transactionTemplate;

    public record GenerateResult(
        List<ApplicationQuestion> allQuestions,
        int generatedCount
    ) {
    }

    public GenerateResult generate(long userId, long applicationId, boolean regenerate) {
        // Phase 1: DB 읽기 (짧은 TX — lazy Document 접근 포함, 즉시 커밋)
        record ReadCtx(
            List<ApplicationQuestion> allQuestions,
            List<ApplicationQuestion> targetQuestions,
            String payload
        ) {}

        ReadCtx ctx = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Application application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ServiceException(
                    ErrorCode.APPLICATION_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "지원 준비를 찾을 수 없습니다."
                ));

            List<ApplicationQuestion> allQuestions =
                applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId);

            if (allQuestions.isEmpty()) {
                throw new ServiceException(
                    ErrorCode.APPLICATION_QUESTION_REQUIRED,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "자소서 문항이 없습니다."
                );
            }

            List<ApplicationQuestion> targetQuestions = regenerate
                ? allQuestions
                : allQuestions.stream()
                    .filter(q -> q.getGeneratedAnswer() == null)
                    .toList();

            // 생성 대상이 없으면 문서 조회/payload 빌드 없이 즉시 반환 (원본 로직 보존)
            if (targetQuestions.isEmpty()) {
                return new ReadCtx(allQuestions, List.of(), null);
            }

            // Lazy 연관 Document 접근 — TX 안에서 수행해야 LazyInitializationException 방지
            List<String> documentTexts = sourceDocumentBindingRepository
                .findAllByApplicationId(applicationId).stream()
                .map(ApplicationSourceDocument::getDocument)
                .filter(doc -> doc.getExtractStatus() == DocumentExtractStatus.SUCCESS)
                .map(Document::getExtractedText)
                .toList();

            List<QuestionInput> questionInputs = targetQuestions.stream()
                .map(q -> new QuestionInput(
                    q.getQuestionOrder(),
                    q.getQuestionText(),
                    q.getToneOption() != null ? q.getToneOption().getValue() : null,
                    q.getLengthOption() != null ? q.getLengthOption().getValue() : null,
                    q.getEmphasisPoint()
                ))
                .toList();

            String payload = payloadBuilder.build(
                application.getJobRole(),
                application.getCompanyName(),
                questionInputs,
                documentTexts
            );

            return new ReadCtx(allQuestions, targetQuestions, payload);
        }));

        if (ctx.targetQuestions().isEmpty()) {
            return new GenerateResult(ctx.allQuestions(), 0);
        }

        // Phase 2: AI 호출 — 트랜잭션 없음, DB 커넥션 미점유
        int maxTokens = calcMaxTokens(ctx.targetQuestions());
        JsonNode responseNode = aiPipeline.executeWithMaxTokens(TEMPLATE_ID, ctx.payload(), maxTokens);

        // 응답 파싱 (메모리, 트랜잭션 불필요)
        Map<Integer, String> answerByOrder = new HashMap<>();
        for (JsonNode answer : responseNode.get("answers")) {
            answerByOrder.put(
                answer.get("questionOrder").asInt(),
                answer.get("answerText").asText()
            );
        }

        // Phase 3: DB 쓰기 (짧은 TX — managed 엔티티로 dirty checking)
        List<ApplicationQuestion> finalQuestions = Objects.requireNonNull(
            transactionTemplate.execute(status -> {
                List<ApplicationQuestion> managedQuestions =
                    applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId);

                Map<Integer, ApplicationQuestion> questionByOrder = managedQuestions.stream()
                    .collect(Collectors.toMap(ApplicationQuestion::getQuestionOrder, Function.identity()));

                answerByOrder.forEach((order, text) -> {
                    ApplicationQuestion q = questionByOrder.get(order);
                    if (q != null) q.updateGeneratedAnswer(text);
                });

                Application managedApp = applicationRepository
                    .findByIdAndUserId(applicationId, userId)
                    .orElseThrow(); // Phase 1에서 이미 검증됨
                applicationStatusService.syncStatus(managedApp);

                return managedQuestions;
            })
        );

        return new GenerateResult(finalQuestions, ctx.targetQuestions().size());
    }

    /**
     * targetQuestions의 lengthOption 합산으로 출력 토큰 상한을 동적 계산.
     * hardMaxChars 기준 한국어 2자 ≈ 1 token, 버퍼 포함.
     * floor: TOKEN_MIN, cap: TOKEN_MAX
     */
    private int calcMaxTokens(List<ApplicationQuestion> questions) {
        int sum = questions.stream()
            .mapToInt(q -> tokensFor(q.getLengthOption()))
            .sum();
        return Math.min(TOKEN_MAX, Math.max(TOKEN_MIN, sum + TOKEN_OVERHEAD));
    }

    private int tokensFor(ApplicationLengthOption option) {
        if (option == null) return TOKEN_PER_MEDIUM;
        return switch (option) {
            case SHORT  -> TOKEN_PER_SHORT;
            case MEDIUM -> TOKEN_PER_MEDIUM;
            case LONG   -> TOKEN_PER_LONG;
        };
    }
}