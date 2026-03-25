package com.back.backend.domain.ai.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.pipeline.payload.SelfIntroPayloadBuilder;
import com.back.backend.domain.ai.pipeline.payload.SelfIntroPayloadBuilder.QuestionInput;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfIntroGenerateService {

    private static final String TEMPLATE_ID = "ai.self_intro.generate.v1";

    private final ApplicationRepository applicationRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicationSourceDocumentBindingRepository sourceDocumentBindingRepository;
    private final SelfIntroPayloadBuilder payloadBuilder;
    private final AiPipeline aiPipeline;

    @Transactional
    public List<ApplicationQuestion> generate(long userId, long applicationId, boolean regenerate) {
        // Application 조회 + 소유권 확인
        Application application = applicationRepository.findByIdAndUserId(applicationId, userId)
            .orElseThrow(() -> new ServiceException(
                ErrorCode.APPLICATION_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "지원 준비를 찾을 수 없습니다."
            ));

        // ApplicationQuestion 목록 조회
        List<ApplicationQuestion> allQuestions =
            applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId);

        if (allQuestions.isEmpty()) {
            throw new ServiceException(
                ErrorCode.APPLICATION_QUESTION_REQUIRED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "자소서 문항이 없습니다."
            );
        }

        // regenerate=false이면 generatedAnswer가 이미 있는 문항 건너뜀
        List<ApplicationQuestion> targetQuestions = regenerate
            ? allQuestions
            : allQuestions.stream()
            .filter(q -> q.getGeneratedAnswer() == null)
            .toList();

        if (targetQuestions.isEmpty()) {
            return allQuestions;
        }

        // ApplicationSourceDocument → Document.extractedText (PII 마스킹 완료 플레인텍스트)
        List<String> documentTexts = sourceDocumentBindingRepository
            .findAllByApplicationId(applicationId).stream()
            .map(ApplicationSourceDocument::getDocument)
            .filter(doc -> doc.getExtractStatus() == DocumentExtractStatus.SUCCESS)
            .map(Document::getExtractedText)
            .toList();

        // SelfIntroPayloadBuilder.build(...)
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

        // AiPipeline.execute
        JsonNode responseNode = aiPipeline.execute(TEMPLATE_ID, payload);

        // JsonNode answers[] → questionOrder 매칭 → updateGeneratedAnswer()
        Map<Integer, ApplicationQuestion> questionByOrder = targetQuestions.stream()
            .collect(Collectors.toMap(ApplicationQuestion::getQuestionOrder, Function.identity()));

        JsonNode answers = responseNode.get("answers");
        for (JsonNode answer : answers) {
            int questionOrder = answer.get("questionOrder").asInt();
            ApplicationQuestion question = questionByOrder.get(questionOrder);
            if (question != null) {
                question.updateGeneratedAnswer(answer.get("answerText").asText());
            }
        }

        // 저장된 문항 목록 반환
        return allQuestions;
    }
}
