package com.back.backend.domain.practice.service;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.FeedbackTagCategory;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.knowledge.repository.KnowledgeItemRepository;
import com.back.backend.domain.practice.dto.request.SubmitPracticeAnswerRequest;
import com.back.backend.domain.practice.dto.response.PracticeSessionResponse;
import com.back.backend.domain.practice.entity.PracticeQuestionType;
import com.back.backend.domain.practice.entity.PracticeSession;
import com.back.backend.domain.practice.entity.PracticeSessionStatus;
import com.back.backend.domain.practice.repository.PracticeSessionRepository;
import com.back.backend.domain.practice.repository.PracticeSessionTagRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PracticeSessionServiceTest {

    @Mock private PracticeSessionRepository sessionRepository;
    @Mock private PracticeSessionTagRepository sessionTagRepository;
    @Mock private KnowledgeItemRepository knowledgeItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private FeedbackTagRepository feedbackTagRepository;
    @Mock private PracticeEvaluationService evaluationService;

    private PracticeSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new PracticeSessionService(
                sessionRepository, sessionTagRepository,
                knowledgeItemRepository, userRepository,
                feedbackTagRepository, evaluationService);
    }

    @Test
    void submitAndEvaluate_success_returnsEvaluatedSession() {
        // given
        User user = User.builder().email("test@test.com").build();
        ReflectionTestUtils.setField(user, "id", 1L);

        KnowledgeItem item = KnowledgeItem.create("gyoogle-tech", "network/tcp.md",
                "TCP란?", "TCP 설명...", "hash");
        ReflectionTestUtils.setField(item, "id", 10L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(knowledgeItemRepository.findById(10L)).willReturn(Optional.of(item));
        given(sessionRepository.save(any(PracticeSession.class)))
                .willAnswer(invocation -> {
                    PracticeSession s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "id", 100L);
                    return s;
                });

        given(evaluationService.evaluate(eq(item), eq(PracticeQuestionType.CS), anyString()))
                .willReturn(new PracticeEvaluationService.EvaluationResult(
                        85, "좋은 답변입니다.", "모범 답안입니다.", List.of("기술 깊이 부족")));

        FeedbackTag tag = FeedbackTag.builder()
                .tagName("기술 깊이 부족").tagCategory(FeedbackTagCategory.TECHNICAL).build();
        ReflectionTestUtils.setField(tag, "id", 5L);
        given(feedbackTagRepository.findAllByTagNameIn(List.of("기술 깊이 부족")))
                .willReturn(List.of(tag));

        // when
        SubmitPracticeAnswerRequest request = new SubmitPracticeAnswerRequest(10L,
                "TCP는 전송 계층 프로토콜로 신뢰성 있는 데이터 전송을 보장합니다. 3-way handshake로 연결을 수립합니다.");
        PracticeSessionResponse response = sessionService.submitAndEvaluate(1L, request);

        // then
        assertThat(response.score()).isEqualTo(85);
        assertThat(response.feedback()).isEqualTo("좋은 답변입니다.");
        assertThat(response.modelAnswer()).isEqualTo("모범 답안입니다.");
        assertThat(response.tagNames()).containsExactly("기술 깊이 부족");
        assertThat(response.status()).isEqualTo("evaluated");

        // 세션이 저장되었는지 확인
        ArgumentCaptor<PracticeSession> captor = ArgumentCaptor.forClass(PracticeSession.class);
        then(sessionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PracticeSessionStatus.EVALUATED);
    }

    @Test
    void submitAndEvaluate_aiClientException_marksSessionFailed() {
        // given
        User user = User.builder().email("test@test.com").build();
        ReflectionTestUtils.setField(user, "id", 1L);

        KnowledgeItem item = KnowledgeItem.create("gyoogle-tech", "network/tcp.md",
                "TCP란?", "TCP 설명...", "hash");
        ReflectionTestUtils.setField(item, "id", 10L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(knowledgeItemRepository.findById(10L)).willReturn(Optional.of(item));
        given(sessionRepository.save(any(PracticeSession.class)))
                .willAnswer(invocation -> {
                    PracticeSession s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "id", 100L);
                    return s;
                });

        given(evaluationService.evaluate(any(), any(), anyString()))
                .willThrow(new AiClientException(AiProvider.GEMINI, ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE, "rate limit"));

        // when & then
        SubmitPracticeAnswerRequest request = new SubmitPracticeAnswerRequest(10L,
                "TCP는 전송 계층 프로토콜로 신뢰성 있는 데이터 전송을 보장합니다. 3-way handshake로 연결을 수립합니다.");

        assertThatThrownBy(() -> sessionService.submitAndEvaluate(1L, request))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE));
    }

    @Test
    void getSessionDetail_notOwner_throwsNotFound() {
        // given
        given(sessionRepository.findByIdAndUserId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sessionService.getSessionDetail(1L, 999L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PRACTICE_SESSION_NOT_FOUND));
    }
}
