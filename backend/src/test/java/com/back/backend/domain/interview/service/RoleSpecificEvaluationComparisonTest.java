package com.back.backend.domain.interview.service;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 직무별 차등 평가 프롬프트 비교 테스트
 *
 * <p>동일한 답변을 서로 다른 jobRole(백엔드/프론트엔드)로 평가하여
 * domainDepth 해석과 피드백이 직무에 맞게 달라지는지 확인한다.
 *
 * <p>실행 전 준비:
 * <ol>
 *   <li>backend/.env.test 파일에 GEMINI_API_KEY=실제키 설정</li>
 *   <li>아래 @Disabled 어노테이션 제거</li>
 *   <li>테스트 실행 (콘솔 출력 확인)</li>
 * </ol>
 */
@IntegrationTest
@TestPropertySource(properties = {
    "spring.config.import=optional:file:${user.dir}/../.env.dev[.properties]",
    "ai.gemini.api-key=${GEMINI_API_KEY}",
    "ai.gemini.base-url=https://generativelanguage.googleapis.com/v1beta",
    "ai.gemini.timeout.read=60s",
    "ai.gemini.retry.max-attempts=1"
})
class RoleSpecificEvaluationComparisonTest {

    private static final String EVALUATE_TEMPLATE_ID = "ai.interview.evaluate.v1";
    private static final String OVERLAY_BASE = "developer/evaluate-role/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AiPipeline aiPipeline;

    @Autowired
    private FeedbackTagRepository feedbackTagRepository;

    @Test
    void 동일한_답변을_백엔드와_프론트엔드_직무로_평가하면_피드백이_달라진다() throws Exception {
        List<FeedbackTag> tagMaster = feedbackTagRepository.findAllByOrderByIdAsc();
        String payload = OBJECT_MAPPER.writeValueAsString(buildTestPayload(tagMaster));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("  직무별 차등 평가 비교 테스트");
        System.out.println("=".repeat(80));

        // 백엔드 평가
        JsonNode backendResult = aiPipeline.execute(
            EVALUATE_TEMPLATE_ID, payload, OVERLAY_BASE + "backend.txt"
        );
        printResult("백엔드 개발자", backendResult);

        // 프론트엔드 평가
        JsonNode frontendResult = aiPipeline.execute(
            EVALUATE_TEMPLATE_ID, payload, OVERLAY_BASE + "frontend.txt"
        );
        printResult("프론트엔드 개발자", frontendResult);

        // default 평가
        JsonNode defaultResult = aiPipeline.execute(
            EVALUATE_TEMPLATE_ID, payload, OVERLAY_BASE + "default.txt"
        );
        printResult("직무 미지정 (default)", defaultResult);

        printComparisonSummary(backendResult, frontendResult, defaultResult);
    }

    /**
     * 테스트용 평가 payload 구성
     * 의도적으로 백엔드/프론트엔드 양쪽에서 다르게 해석될 수 있는 답변을 사용
     */
    private Map<String, Object> buildTestPayload(List<FeedbackTag> tagMaster) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", 9999);
        payload.put("questionSetId", 9999);
        payload.put("jobRole", "개발자");
        payload.put("tagMaster", tagMaster.stream()
            .map(tag -> Map.of(
                "tagId", tag.getId(),
                "tagName", tag.getTagName(),
                "tagCategory", tag.getTagCategory().getValue()
            ))
            .toList());
        payload.put("answers", List.of(
            Map.of(
                "questionOrder", 1,
                "questionId", 1001,
                "questionType", "technical_stack",
                "difficultyLevel", "medium",
                "questionText", "프로젝트에서 기술 스택을 어떤 기준으로 선택했나요?",
                "answerText", "Spring Boot와 React를 사용했습니다. 백엔드는 Spring Boot의 자동 설정과 "
                    + "내장 서버가 빠른 개발에 유리했고, JPA로 DB 접근을 추상화했습니다. "
                    + "프론트엔드는 React의 컴포넌트 기반 구조가 UI 재사용에 좋았고, "
                    + "Redux로 전역 상태를 관리했습니다. 배포는 Docker로 컨테이너화하여 "
                    + "환경 차이 문제를 해결했습니다.",
                "isSkipped", false
            ),
            Map.of(
                "questionOrder", 2,
                "questionId", 1002,
                "questionType", "experience",
                "difficultyLevel", "medium",
                "questionText", "성능 문제를 해결한 경험이 있나요?",
                "answerText", "사용자가 증가하면서 페이지 로딩이 느려지는 문제가 발생했습니다. "
                    + "프론트엔드에서는 코드 스플리팅과 이미지 lazy loading을 적용했고, "
                    + "백엔드에서는 N+1 쿼리를 fetch join으로 개선하고 Redis 캐싱을 도입했습니다. "
                    + "그 결과 전체 페이지 로딩 시간이 3초에서 1.2초로 단축되었습니다.",
                "isSkipped", false
            )
        ));
        return payload;
    }

    private void printResult(String roleName, JsonNode result) {
        System.out.println("\n┌─ " + roleName + " 평가 " + "─".repeat(Math.max(0, 68 - roleName.length() * 2)) + "┐");
        System.out.println("│ 총점: " + result.path("totalScore").asInt());
        System.out.println("│ 요약: " + result.path("summaryFeedback").asText());
        System.out.println("│");

        JsonNode answers = result.path("answers");
        for (JsonNode answer : answers) {
            int order = answer.path("questionOrder").asInt();
            int score = answer.path("score").asInt();
            String rationale = answer.path("evaluationRationale").asText();
            List<String> tags = new java.util.ArrayList<>();
            answer.path("tagNames").forEach(tag -> tags.add(tag.asText()));

            System.out.println("│ [질문 " + order + "] 점수: " + score);

            // rationale을 ①②③으로 분리하여 출력
            String[] parts = rationale.split("[①②③]");
            if (parts.length >= 4) {
                System.out.println("│   ① " + parts[1].trim());
                System.out.println("│   ② " + parts[2].trim());
                System.out.println("│   ③ " + parts[3].trim());
            } else {
                System.out.println("│   " + rationale);
            }
            System.out.println("│   태그: " + tags);
            System.out.println("│");
        }
        System.out.println("└" + "─".repeat(78) + "┘");
    }

    private void printComparisonSummary(JsonNode backend, JsonNode frontend, JsonNode defaultResult) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  비교 요약");
        System.out.println("=".repeat(80));
        System.out.printf("  %-20s | %-10s | %-10s | %-10s%n", "", "백엔드", "프론트엔드", "default");
        System.out.println("  " + "-".repeat(60));
        System.out.printf("  %-20s | %-10d | %-10d | %-10d%n",
            "총점",
            backend.path("totalScore").asInt(),
            frontend.path("totalScore").asInt(),
            defaultResult.path("totalScore").asInt());

        for (int i = 0; i < backend.path("answers").size(); i++) {
            System.out.printf("  %-20s | %-10d | %-10d | %-10d%n",
                "질문 " + (i + 1) + " 점수",
                backend.path("answers").get(i).path("score").asInt(),
                frontend.path("answers").get(i).path("score").asInt(),
                defaultResult.path("answers").get(i).path("score").asInt());
        }
        System.out.println("=".repeat(80));
        System.out.println("  ※ 핵심 확인 포인트:");
        System.out.println("    - 질문1(기술 스택): 백엔드는 JPA/DB 관점, 프론트는 React/상태관리 관점으로 피드백이 달라지는가?");
        System.out.println("    - 질문2(성능): 백엔드는 N+1/캐싱 깊이, 프론트는 코드 스플리팅/Core Web Vitals 깊이로 평가하는가?");
        System.out.println("=".repeat(80) + "\n");
    }
}
