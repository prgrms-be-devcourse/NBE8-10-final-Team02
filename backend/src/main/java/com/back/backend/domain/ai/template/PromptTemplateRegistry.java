package com.back.backend.domain.ai.template;

import com.back.backend.domain.ai.client.AiProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * 프롬프트 템플릿 레지스트리
 * 기본 템플릿을 등록하고 templateId로 조회
 * 생성 후 불변 — 외부에서 추가/수정 불가
 */
public class PromptTemplateRegistry {

    private final Map<String, PromptTemplate> templates;

    private PromptTemplateRegistry(Map<String, PromptTemplate> templates) {
        this.templates = templates;
    }

    public PromptTemplate get(String templateId) {
        PromptTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("등록되지 않은 templateId: " + templateId);
        }
        return template;
    }

    /**
     * 기본 템플릿이 모두 등록된 불변 레지스트리를 생성
     *
     * preferredProvider 배정 전략:
     *   - VERTEX_AI: 품질/정확도가 중요하거나 대용량 토큰이 필요한 작업 (포트폴리오, 면접 평가, 질문 생성)
     *   - GROQ: 경량 작업 또는 빈도가 높은 작업 (꼬리 질문, 요약, 자소서, 문제 평가)
     *   - Gemini 무료 티어(RPD 250)의 부하를 분산하고 Groq 무료 용량을 적극 활용하기 위함
     */
    public static PromptTemplateRegistry createDefault() {
        Map<String, PromptTemplate> map = new HashMap<>();

        map.put("ai.portfolio.summary.v1", new PromptTemplate(
            "ai.portfolio.summary.v1", "v1", "portfolio_summary",
            "system/common-system.txt",
            "developer/ai.portfolio.summary.v1.txt",
            "schema/portfolio-summary.schema.json",
            0.2, 4000, // 레포지토리 분석 요약이 잘리지 않도록 토큰 여유 확보
            new PromptTemplate.RetryPolicy(2, false),
            AiProvider.VERTEX_AI, // 대용량 분석, 품질 중요
            false
        ));

        map.put("ai.self_intro.generate.v1", new PromptTemplate(
            "ai.self_intro.generate.v1", "v1", "self_intro_generation",
            "system/common-system.txt",
            "developer/ai.self_intro.generate.v1.txt",
            "schema/self-intro-generate.schema.json",
            0.5, 4000,
            new PromptTemplate.RetryPolicy(2, false),
            AiProvider.GROQ, // 경량 작업, Llama 3.3으로 충분한 품질
            false
        ));

        map.put("ai.interview.questions.generate.v1", new PromptTemplate(
            "ai.interview.questions.generate.v1", "v1", "interview_question_generation",
            "system/common-system.txt",
            "developer/ai.interview.questions.generate.v1.txt",
            "schema/interview-questions-generate.schema.json",
            0.6, 4000,
            new PromptTemplate.RetryPolicy(2, false),
            AiProvider.VERTEX_AI, // 포트폴리오 기반, 정확도 중요
            false
        ));

        map.put("ai.interview.followup.generate.v1", new PromptTemplate(
            "ai.interview.followup.generate.v1", "v1", "interview_followup_generation",
            "system/common-system.txt",
            "developer/ai.interview.followup.generate.v1.txt",
            "schema/interview-followup-generate.schema.json",
            0.5, 1000,
            new PromptTemplate.RetryPolicy(1, false),
            AiProvider.GROQ, // 1000토큰 경량, 빈도 최다
            false
        ));

        map.put("ai.interview.followup.complete.v1", new PromptTemplate(
            "ai.interview.followup.complete.v1", "v1", "interview_followup_completion",
            "system/common-system.txt",
            "developer/ai.interview.followup.complete.v1.txt",
            "schema/interview-followup-complete.schema.json",
            0.0, 300, // completion follow-up은 고정된 짧은 JSON 1개만 필요하므로 최대한 결정적으로 생성
            new PromptTemplate.RetryPolicy(1, false),
            AiProvider.GROQ, // 300토큰 초경량
            false
        ));

        map.put("ai.interview.evaluate.v1", new PromptTemplate(
            "ai.interview.evaluate.v1", "v1", "interview_evaluation",
            "system/common-system.txt",
            "developer/ai.interview.evaluate.v1.txt",
            "schema/interview-evaluate.schema.json",
            0.4, 8000, // 질문 최대 20개 × 평가 항목(score, rationale, tags) 감안하여 토큰 여유 확보. 피드백 다양성을 위해 temperature 0.2→0.4
            new PromptTemplate.RetryPolicy(2, false),
            AiProvider.VERTEX_AI, // 평가 정확도 최우선
            false
        ));

        map.put("ai.interview.summary.v1", new PromptTemplate(
            "ai.interview.summary.v1", "v1", "interview_summary",
            "system/common-system.txt",
            "developer/ai.interview.summary.v1.txt",
            "schema/interview-summary.schema.json",
            0.3, 1500,
            new PromptTemplate.RetryPolicy(1, false),
            AiProvider.GROQ, // 1500토큰 경량
            false
        ));

        // Batch 포트폴리오 요약 템플릿 (여러 repo → 청크 단위 AI 호출)
        // maxTokens: runtime에 BatchProviderStrategy로 override됨 (Gemini=8000, Vertex=64000)
        // schemaFile: null — 배열 응답이라 기존 schema 재사용 불가, BatchPortfolioSummaryValidator가 직접 검증
        // allowPartialRecovery: true — JSON 절단 시 완성된 원소만 저장
        map.put("ai.portfolio.summary.batch.v1", new PromptTemplate(
            "ai.portfolio.summary.batch.v1", "v1", "portfolio_summary_batch",
            "system/common-system.txt",
            "developer/ai.portfolio.summary.batch.v1.txt",
            null,   // JSON Schema 파일 없음 (배열 검증은 BatchPortfolioSummaryValidator에서 수행)
            0.2, 8000, // 기본값 — executeWithMaxTokens()로 runtime override
            new PromptTemplate.RetryPolicy(1, false), // retry=1: 절단된 JSON 등 일시적 오류 시 1회 재시도
            AiProvider.VERTEX_AI, // 대용량 배치, 토큰 무제한 필요
            true  // 절단된 JSON에서 완성된 repo만 추출하여 부분 저장
        ));

        // 문제은행 연습 — CS 답변 평가
        // maxTokens: 한국어 feedback(3문장)+modelAnswer(3~5문장)가 영어 대비 토큰 2~3배 소비하므로 여유 확보
        map.put("ai.practice.evaluate.cs.v1", new PromptTemplate(
            "ai.practice.evaluate.cs.v1", "v1", "practice_evaluate_cs",
            "system/common-system.txt",
            "developer/ai.practice.evaluate.cs.v1.txt",
            "practice-evaluate.schema.json",
            0.3, 4000,
            new PromptTemplate.RetryPolicy(2, true),
            AiProvider.GROQ, // allowFallback=true, 무료 provider 우선
            false
        ));

        // 문제은행 연습 — 인성 답변 평가
        map.put("ai.practice.evaluate.behavioral.v1", new PromptTemplate(
            "ai.practice.evaluate.behavioral.v1", "v1", "practice_evaluate_behavioral",
            "system/common-system.txt",
            "developer/ai.practice.evaluate.behavioral.v1.txt",
            "practice-evaluate.schema.json",
            0.3, 4000,
            new PromptTemplate.RetryPolicy(2, true),
            AiProvider.GROQ, // allowFallback=true, 무료 provider 우선
            false
        ));

        // Map.copyOf()로 불변 맵 생성 — 이후 수정 시도 시 UnsupportedOperationException
        return new PromptTemplateRegistry(Map.copyOf(map));
    }
}
