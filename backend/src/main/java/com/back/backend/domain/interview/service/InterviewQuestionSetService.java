package com.back.backend.domain.interview.service;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.dto.request.CreateInterviewQuestionSetRequest;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.InterviewQuestionSetDetailResponse;
import com.back.backend.domain.interview.dto.response.InterviewQuestionSetSummaryResponse;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.mapper.InterviewResponseMapper;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.response.FieldErrorDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewQuestionSetService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final InterviewQuestionGenerationService interviewQuestionGenerationService;
    private final InterviewQuestionSetRepository interviewQuestionSetRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewResponseMapper interviewResponseMapper;
    private final TransactionTemplate transactionTemplate;

    public InterviewQuestionSetSummaryResponse createQuestionSet(
            long userId,
            CreateInterviewQuestionSetRequest request
    ) {
        long applicationId = requirePositiveId(request.applicationId(), "applicationId");
        int questionCount = requireQuestionCount(request.questionCount());
        DifficultyLevel difficultyLevel = parseRequiredEnum(
                request.difficultyLevel(),
                DifficultyLevel.class,
                "difficultyLevel"
        );
        List<InterviewQuestionType> questionTypes = requireQuestionTypes(request.questionTypes());

        InterviewQuestionGenerationService.GeneratedQuestionSet generatedQuestionSet =
                interviewQuestionGenerationService.generate(
                        userId,
                        applicationId,
                        questionCount,
                        difficultyLevel,
                        questionTypes,
                        request.title()
                );

        return Objects.requireNonNull(transactionTemplate.execute(transactionStatus ->
                persistGeneratedQuestionSet(userId, generatedQuestionSet)
        ));
    }

    @Transactional(readOnly = true)
    public List<InterviewQuestionSetSummaryResponse> getQuestionSets(long userId) {
        return interviewQuestionSetRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(interviewResponseMapper::toInterviewQuestionSetSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewQuestionSetDetailResponse getQuestionSetDetail(long userId, long questionSetId) {
        InterviewQuestionSet questionSet = getOwnedQuestionSet(userId, questionSetId);
        List<InterviewQuestionResponse> questions =
                interviewQuestionRepository.findAllByQuestionSetIdOrderByQuestionOrderAsc(questionSetId).stream()
                        .map(interviewResponseMapper::toInterviewQuestionResponse)
                        .toList();

        return interviewResponseMapper.toInterviewQuestionSetDetailResponse(questionSet, questions);
    }

    @Transactional
    public InterviewQuestionResponse addQuestion(
            long userId,
            long questionSetId,
            AddInterviewQuestionRequest request
    ) {
        InterviewQuestionSet questionSet = getOwnedQuestionSet(userId, questionSetId);
        validateEditable(questionSet.getId());

        String questionText = requireQuestionText(request.questionText());
        InterviewQuestionType questionType = parseManualQuestionType(request.questionType());
        DifficultyLevel difficultyLevel = parseRequiredEnum(
                request.difficultyLevel(),
                DifficultyLevel.class,
                "difficultyLevel"
        );

        int nextOrder = interviewQuestionRepository.findTopByQuestionSetIdOrderByQuestionOrderDesc(questionSetId)
                .map(question -> question.getQuestionOrder() + 1)
                .orElse(1);

        InterviewQuestion savedQuestion = interviewQuestionRepository.save(
                InterviewQuestion.builder()
                        .questionSet(questionSet)
                        .parentQuestion(null)
                        .sourceApplicationQuestion(null)
                        .questionOrder(nextOrder)
                        .questionType(questionType)
                        .difficultyLevel(difficultyLevel)
                        .questionText(questionText)
                        .build()
        );

        questionSet.changeQuestionCount(nextOrder);
        return interviewResponseMapper.toInterviewQuestionResponse(savedQuestion);
    }

    @Transactional
    public void deleteQuestion(long userId, long questionSetId, long questionId) {
        InterviewQuestionSet questionSet = getOwnedQuestionSet(userId, questionSetId);
        validateEditable(questionSet.getId());

        InterviewQuestion question = interviewQuestionRepository.findByIdAndQuestionSetId(questionId, questionSetId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "질문을 찾을 수 없습니다."
                ));

        List<InterviewQuestion> questions =
                interviewQuestionRepository.findAllByQuestionSetIdOrderByQuestionOrderAsc(questionSetId);
        if (questions.size() <= 1) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "질문 세트는 최소 1개 질문을 유지해야 합니다.",
                    false,
                    List.of(new FieldErrorDetail("questionId", "min_1"))
            );
        }

        interviewQuestionRepository.delete(question);
        interviewQuestionRepository.flush();

        List<InterviewQuestion> remainingQuestions = questions.stream()
                .filter(candidate -> !candidate.getId().equals(questionId))
                .toList();

        // question_order는 질문 세트 안에서 면접 진행 순서를 뜻하므로 중간 삭제 후 연속 값으로 다시 맞춘다.
        // 재정렬하지 않으면 이후 수동 추가와 세션 진행 순서가 빈 번호를 기준으로 어긋날 수 있다.
        for (int index = 0; index < remainingQuestions.size(); index++) {
            remainingQuestions.get(index).changeQuestionOrder(index + 1);
        }

        questionSet.changeQuestionCount(remainingQuestions.size());
    }

    private InterviewQuestionSetSummaryResponse persistGeneratedQuestionSet(
            long userId,
            InterviewQuestionGenerationService.GeneratedQuestionSet generatedQuestionSet
    ) {
        Application application = applicationRepository.findByIdAndUserId(generatedQuestionSet.applicationId(), userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.APPLICATION_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "지원 준비를 찾을 수 없습니다."
                ));

        Map<Integer, ApplicationQuestion> sourceQuestionByOrder =
                applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(application.getId()).stream()
                        .collect(Collectors.toMap(
                                ApplicationQuestion::getQuestionOrder,
                                applicationQuestion -> applicationQuestion
                        ));

        InterviewQuestionSet questionSet = interviewQuestionSetRepository.save(
                InterviewQuestionSet.builder()
                        .user(application.getUser())
                        .application(application)
                        .title(generatedQuestionSet.title())
                        .questionCount(generatedQuestionSet.questions().size())
                        .difficultyLevel(generatedQuestionSet.difficultyLevel())
                        .questionTypes(generatedQuestionSet.questionTypes().stream()
                                .map(InterviewQuestionType::getValue)
                                .toArray(String[]::new))
                        .build()
        );

        Map<Integer, InterviewQuestion> savedQuestionsByOrder = new LinkedHashMap<>();
        for (InterviewQuestionGenerationService.GeneratedQuestion generatedQuestion : generatedQuestionSet.questions()) {
            InterviewQuestion savedQuestion = interviewQuestionRepository.save(
                    InterviewQuestion.builder()
                            .questionSet(questionSet)
                            .parentQuestion(generatedQuestion.parentQuestionOrder() == null
                                    ? null
                                    : savedQuestionsByOrder.get(generatedQuestion.parentQuestionOrder()))
                            .sourceApplicationQuestion(generatedQuestion.sourceApplicationQuestionOrder() == null
                                    ? null
                                    : sourceQuestionByOrder.get(generatedQuestion.sourceApplicationQuestionOrder()))
                            .questionOrder(generatedQuestion.questionOrder())
                            .questionType(generatedQuestion.questionType())
                            .difficultyLevel(generatedQuestion.difficultyLevel())
                            .questionText(generatedQuestion.questionText())
                            .build()
            );
            savedQuestionsByOrder.put(savedQuestion.getQuestionOrder(), savedQuestion);
        }

        return interviewResponseMapper.toInterviewQuestionSetSummaryResponse(questionSet);
    }

    private InterviewQuestionSet getOwnedQuestionSet(long userId, long questionSetId) {
        return interviewQuestionSetRepository.findByIdAndUserId(questionSetId, userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "질문 세트를 찾을 수 없습니다."
                ));
    }

    private void validateEditable(long questionSetId) {
        // 세션이 한 번이라도 생성된 질문 세트는 과거 질문 순서와 답변 문맥을 보존해야 하므로 잠근다.
        if (interviewSessionRepository.existsByQuestionSetId(questionSetId)) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_QUESTION_SET_NOT_EDITABLE,
                    HttpStatus.CONFLICT,
                    "이미 면접이 시작된 질문 세트는 수정할 수 없습니다."
            );
        }
    }

    private long requirePositiveId(Long value, String field) {
        if (value == null) {
            throw requestValidationException(field, "required");
        }
        if (value <= 0) {
            throw requestValidationException(field, "invalid");
        }
        return value;
    }

    private int requireQuestionCount(Integer questionCount) {
        if (questionCount == null) {
            throw requestValidationException("questionCount", "required");
        }
        if (questionCount < 1 || questionCount > 20) {
            throw requestValidationException("questionCount", "out_of_range");
        }
        return questionCount;
    }

    private List<InterviewQuestionType> requireQuestionTypes(List<String> rawQuestionTypes) {
        if (rawQuestionTypes == null || rawQuestionTypes.isEmpty()) {
            throw requestValidationException("questionTypes", "required");
        }

        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        LinkedHashSet<InterviewQuestionType> normalizedQuestionTypes = new LinkedHashSet<>();

        for (String rawQuestionType : rawQuestionTypes) {
            String normalizedQuestionType = normalizeOptionalText(rawQuestionType);
            if (normalizedQuestionType == null) {
                fieldErrors.add(new FieldErrorDetail("questionTypes", "invalid"));
                continue;
            }

            Arrays.stream(InterviewQuestionType.values())
                    .filter(candidate -> candidate.getValue().equals(normalizedQuestionType))
                    .findFirst()
                    .ifPresentOrElse(
                            normalizedQuestionTypes::add,
                            () -> fieldErrors.add(new FieldErrorDetail("questionTypes", "invalid"))
                    );
        }

        if (!fieldErrors.isEmpty()) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    fieldErrors
            );
        }

        if (normalizedQuestionTypes.isEmpty()) {
            throw requestValidationException("questionTypes", "required");
        }

        return List.copyOf(normalizedQuestionTypes);
    }

    private String requireQuestionText(String questionText) {
        String normalized = normalizeOptionalText(questionText);
        if (normalized == null) {
            throw requestValidationException("questionText", "blank");
        }
        return normalized;
    }

    private InterviewQuestionType parseManualQuestionType(String rawValue) {
        InterviewQuestionType questionType = parseRequiredEnum(rawValue, InterviewQuestionType.class, "questionType");
        // follow_up은 이전 질문과 답변 문맥을 전제로 생성되는 질문이라 수동 추가 대상으로 열지 않는다.
        if (questionType == InterviewQuestionType.FOLLOW_UP) {
            throw requestValidationException("questionType", "invalid");
        }
        return questionType;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private <E extends Enum<E> & StringCodeEnum> E parseRequiredEnum(
            String rawValue,
            Class<E> enumType,
            String field
    ) {
        String normalized = normalizeOptionalText(rawValue);
        if (normalized == null) {
            throw requestValidationException(field, "required");
        }

        return Arrays.stream(enumType.getEnumConstants())
                .filter(candidate -> candidate.getValue().equals(normalized))
                .findFirst()
                .orElseThrow(() -> requestValidationException(field, "invalid"));
    }

    private ServiceException requestValidationException(String field, String reason) {
        return new ServiceException(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                HttpStatus.BAD_REQUEST,
                "요청 값을 다시 확인해주세요.",
                false,
                List.of(new FieldErrorDetail(field, reason))
        );
    }
}
