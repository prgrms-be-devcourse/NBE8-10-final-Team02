package com.back.backend.domain.interview.service;

import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
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

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewQuestionSetService {

    private final InterviewQuestionSetRepository interviewQuestionSetRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewResponseMapper interviewResponseMapper;

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
        DifficultyLevel difficultyLevel = parseEnum(
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

        List<InterviewQuestion> questions = interviewQuestionRepository.findAllByQuestionSetIdOrderByQuestionOrderAsc(questionSetId);
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

    private String requireQuestionText(String questionText) {
        String normalized = normalizeOptionalText(questionText);
        if (normalized == null) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail("questionText", "blank"))
            );
        }
        return normalized;
    }

    private InterviewQuestionType parseManualQuestionType(String rawValue) {
        InterviewQuestionType questionType = parseEnum(rawValue, InterviewQuestionType.class, "questionType");
        // follow_up은 이전 질문과 답변 문맥을 전제로 생성되는 질문이라 수동 추가 대상으로 열지 않는다.
        if (questionType == InterviewQuestionType.FOLLOW_UP) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail("questionType", "invalid"))
            );
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

    private <E extends Enum<E> & StringCodeEnum> E parseEnum(
            String rawValue,
            Class<E> enumType,
            String field
    ) {
        String normalized = normalizeOptionalText(rawValue);
        if (normalized == null) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail(field, "required"))
            );
        }

        return Arrays.stream(enumType.getEnumConstants())
                .filter(candidate -> candidate.getValue().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.REQUEST_VALIDATION_FAILED,
                        HttpStatus.BAD_REQUEST,
                        "요청 값을 다시 확인해주세요.",
                        false,
                        List.of(new FieldErrorDetail(field, "invalid"))
                ));
    }
}
