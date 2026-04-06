package com.back.backend.domain.practice.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.knowledge.repository.KnowledgeItemRepository;
import com.back.backend.domain.practice.dto.request.SubmitPracticeAnswerRequest;
import com.back.backend.domain.practice.dto.response.PracticeSessionDetailResponse;
import com.back.backend.domain.practice.dto.response.PracticeSessionResponse;
import com.back.backend.domain.practice.entity.PracticeQuestionType;
import com.back.backend.domain.practice.entity.PracticeSession;
import com.back.backend.domain.practice.entity.PracticeSessionTag;
import com.back.backend.domain.practice.repository.PracticeSessionRepository;
import com.back.backend.domain.practice.repository.PracticeSessionTagRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class PracticeSessionService {

    private static final Logger log = LoggerFactory.getLogger(PracticeSessionService.class);

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeSessionTagRepository practiceSessionTagRepository;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final UserRepository userRepository;
    private final FeedbackTagRepository feedbackTagRepository;
    private final PracticeEvaluationService evaluationService;

    public PracticeSessionService(PracticeSessionRepository practiceSessionRepository,
                                  PracticeSessionTagRepository practiceSessionTagRepository,
                                  KnowledgeItemRepository knowledgeItemRepository,
                                  UserRepository userRepository,
                                  FeedbackTagRepository feedbackTagRepository,
                                  PracticeEvaluationService evaluationService) {
        this.practiceSessionRepository = practiceSessionRepository;
        this.practiceSessionTagRepository = practiceSessionTagRepository;
        this.knowledgeItemRepository = knowledgeItemRepository;
        this.userRepository = userRepository;
        this.feedbackTagRepository = feedbackTagRepository;
        this.evaluationService = evaluationService;
    }

    @Transactional
    public PracticeSessionResponse submitAndEvaluate(long userId, SubmitPracticeAnswerRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "사용자를 찾을 수 없습니다."));

        KnowledgeItem item = knowledgeItemRepository.findById(request.knowledgeItemId())
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "질문을 찾을 수 없습니다: " + request.knowledgeItemId()));

        PracticeQuestionType questionType = PracticeQuestionType.fromSourceKey(item.getSourceKey());
        PracticeSession session = PracticeSession.create(user, item, questionType, request.answerText());
        practiceSessionRepository.save(session);

        try {
            PracticeEvaluationService.EvaluationResult result =
                    evaluationService.evaluate(item, questionType, request.answerText());

            // 모범답안 캐싱: 첫 평가 시 저장, 이후 재사용
            String modelAnswer;
            if (item.hasModelAnswer()) {
                modelAnswer = item.getModelAnswer();
            } else {
                modelAnswer = result.modelAnswer();
                item.cacheModelAnswer(modelAnswer);
            }
            session.applyEvaluation(result.score(), result.feedback(), modelAnswer);

            List<String> tagNames = saveTagsAndGetNames(session, result.tagNames());

            return PracticeSessionResponse.of(session, tagNames);
        } catch (AiClientException e) {
            session.markFailed();
            log.error("문제은행 AI 평가 실패 (AI 클라이언트): sessionId={}", session.getId(), e);
            throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 서비스가 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");
        } catch (ServiceException e) {
            session.markFailed();
            log.error("문제은행 AI 평가 실패 (서비스): sessionId={}", session.getId(), e);
            throw new ServiceException(ErrorCode.PRACTICE_EVALUATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "답변 평가에 실패했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RuntimeException e) {
            session.markFailed();
            log.error("문제은행 AI 평가 실패 (예상치 못한 오류): sessionId={}", session.getId(), e);
            throw new ServiceException(ErrorCode.PRACTICE_EVALUATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "답변 평가 중 오류가 발생했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Page<PracticeSessionResponse> getSessions(long userId, String questionType, Pageable pageable) {
        Page<PracticeSession> sessions;
        if (questionType != null && !questionType.isBlank()) {
            PracticeQuestionType type = "behavioral".equalsIgnoreCase(questionType)
                    ? PracticeQuestionType.BEHAVIORAL : PracticeQuestionType.CS;
            sessions = practiceSessionRepository.findAllByUserIdAndQuestionTypeOrderByCreatedAtDesc(
                    userId, type, pageable);
        } else {
            sessions = practiceSessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return sessions.map(session -> {
            List<String> tagNames = practiceSessionTagRepository.findAllWithTagBySessionId(session.getId())
                    .stream()
                    .map(pst -> pst.getFeedbackTag().getTagName())
                    .toList();
            return PracticeSessionResponse.of(session, tagNames);
        });
    }

    @Transactional(readOnly = true)
    public PracticeSessionDetailResponse getSessionDetail(long userId, long sessionId) {
        PracticeSession session = practiceSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.PRACTICE_SESSION_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "연습 기록을 찾을 수 없습니다."));

        List<String> tagNames = practiceSessionTagRepository.findAllWithTagBySessionId(sessionId)
                .stream()
                .map(pst -> pst.getFeedbackTag().getTagName())
                .toList();

        return PracticeSessionDetailResponse.of(session, tagNames);
    }

    private List<String> saveTagsAndGetNames(PracticeSession session, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<FeedbackTag> matchedTags = feedbackTagRepository.findAllByTagNameIn(tagNames);
        for (FeedbackTag tag : matchedTags) {
            practiceSessionTagRepository.save(
                    PracticeSessionTag.builder()
                            .practiceSession(session)
                            .feedbackTag(tag)
                            .build()
            );
        }

        return matchedTags.stream().map(FeedbackTag::getTagName).toList();
    }
}
