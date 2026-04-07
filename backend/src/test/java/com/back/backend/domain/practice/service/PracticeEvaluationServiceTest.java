package com.back.backend.domain.practice.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.FeedbackTagCategory;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.practice.entity.PracticeQuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PracticeEvaluationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private AiPipeline aiPipeline;

    @Mock
    private FeedbackTagRepository feedbackTagRepository;

    private PracticeEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new PracticeEvaluationService(aiPipeline, feedbackTagRepository);
    }

    @Test
    void evaluate_csQuestion_returnsMappedResult() throws Exception {
        // given
        KnowledgeItem item = KnowledgeItem.create("gyoogle-tech", "network/tcp.md",
                "TCP 3-way handshake란?", "TCP 연결 수립 과정 설명...", "hash123");
        ReflectionTestUtils.setField(item, "id", 1L);

        List<FeedbackTag> tagMaster = List.of(
                feedbackTag(1L, "기술 깊이 부족", FeedbackTagCategory.TECHNICAL),
                feedbackTag(2L, "구체성 부족", FeedbackTagCategory.CONTENT)
        );
        given(feedbackTagRepository.findAll()).willReturn(tagMaster);

        given(aiPipeline.execute(eq("ai.practice.evaluate.cs.v1"), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "score": 75,
                          "feedback": "SYN-ACK 과정을 정확히 설명했다. 4-way handshake와의 비교가 없다. FIN 과정과 연결하여 설명하면 좋겠다.",
                          "modelAnswer": "TCP 3-way handshake는 SYN → SYN-ACK → ACK 과정으로 연결을 수립합니다.",
                          "tagNames": ["기술 깊이 부족"]
                        }
                        """));

        // when
        PracticeEvaluationService.EvaluationResult result =
                evaluationService.evaluate(item, PracticeQuestionType.CS, "TCP는 SYN-ACK로 연결합니다.");

        // then
        assertThat(result.score()).isEqualTo(75);
        assertThat(result.feedback()).contains("SYN-ACK");
        assertThat(result.modelAnswer()).contains("3-way handshake");
        assertThat(result.tagNames()).containsExactly("기술 깊이 부족");
    }

    @Test
    void evaluate_behavioralQuestion_usesCorrectTemplate() throws Exception {
        // given
        KnowledgeItem item = KnowledgeItem.create("local-behavioral", "data/behavioral-questions.json",
                "B_001 [standard]", "1분 자기소개를 해주세요.\n\n[AI Guide]\n핵심 강점과 경험을 간결하게 전달했는지 평가",
                "hash456");
        ReflectionTestUtils.setField(item, "id", 2L);

        given(feedbackTagRepository.findAll()).willReturn(List.of());

        given(aiPipeline.execute(eq("ai.practice.evaluate.behavioral.v1"), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "score": 60,
                          "feedback": "직무 연관성을 언급했다. STAR 구조가 부족하다. 구체적 성과를 수치로 보완하면 좋겠다.",
                          "modelAnswer": "이런 구조로 답하면 좋습니다: 핵심 역량 → 관련 경험 → 지원 동기 순서로 구성하세요.",
                          "tagNames": []
                        }
                        """));

        // when
        PracticeEvaluationService.EvaluationResult result =
                evaluationService.evaluate(item, PracticeQuestionType.BEHAVIORAL, "저는 개발자입니다...");

        // then
        assertThat(result.score()).isEqualTo(60);
        assertThat(result.feedback()).contains("STAR");
        assertThat(result.modelAnswer()).contains("핵심 역량");
        assertThat(result.tagNames()).isEmpty();
    }

    private FeedbackTag feedbackTag(Long id, String name, FeedbackTagCategory category) {
        FeedbackTag tag = FeedbackTag.builder()
                .tagName(name)
                .tagCategory(category)
                .build();
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }
}
