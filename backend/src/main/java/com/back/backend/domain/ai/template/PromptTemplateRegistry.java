package com.back.backend.domain.ai.template;

import java.util.HashMap;
import java.util.Map;

/**
 * 프롬프트 템플릿 레지스트리
 * 6개 템플릿을 등록하고 templateId로 조회
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
     * 6개 기본 템플릿이 모두 등록된 불변 레지스트리를 생성
     */
    public static PromptTemplateRegistry createDefault() {
        Map<String, PromptTemplate> map = new HashMap<>();

        map.put("ai.portfolio.summary.v1", new PromptTemplate(
            "ai.portfolio.summary.v1", "v1", "portfolio_summary",
            "system/common-system.txt",
            "developer/ai.portfolio.summary.v1.txt",
            "schema/portfolio-summary.schema.json",
            0.2, 4000, // 레포지토리 분석 요약이 잘리지 않도록 토큰 여유 확보
            new PromptTemplate.RetryPolicy(2, false)
        ));

        map.put("ai.self_intro.generate.v1", new PromptTemplate(
            "ai.self_intro.generate.v1", "v1", "self_intro_generation",
            "system/common-system.txt",
            "developer/ai.self_intro.generate.v1.txt",
            "schema/self-intro-generate.schema.json",
            0.5, 4000,
            new PromptTemplate.RetryPolicy(2, false)
        ));

        map.put("ai.interview.questions.generate.v1", new PromptTemplate(
            "ai.interview.questions.generate.v1", "v1", "interview_question_generation",
            "system/common-system.txt",
            "developer/ai.interview.questions.generate.v1.txt",
            "schema/interview-questions-generate.schema.json",
            0.6, 4000,
            new PromptTemplate.RetryPolicy(2, false)
        ));

        map.put("ai.interview.followup.generate.v1", new PromptTemplate(
            "ai.interview.followup.generate.v1", "v1", "interview_followup_generation",
            "system/common-system.txt",
            "developer/ai.interview.followup.generate.v1.txt",
            "schema/interview-followup-generate.schema.json",
            0.5, 1000,
            new PromptTemplate.RetryPolicy(1, false)
        ));

        map.put("ai.interview.evaluate.v1", new PromptTemplate(
            "ai.interview.evaluate.v1", "v1", "interview_evaluation",
            "system/common-system.txt",
            "developer/ai.interview.evaluate.v1.txt",
            "schema/interview-evaluate.schema.json",
            0.4, 8000, // 질문 최대 20개 × 평가 항목(score, rationale, tags) 감안하여 토큰 여유 확보. 피드백 다양성을 위해 temperature 0.2→0.4
            new PromptTemplate.RetryPolicy(2, false)
        ));

        map.put("ai.interview.summary.v1", new PromptTemplate(
            "ai.interview.summary.v1", "v1", "interview_summary",
            "system/common-system.txt",
            "developer/ai.interview.summary.v1.txt",
            "schema/interview-summary.schema.json",
            0.3, 1500,
            new PromptTemplate.RetryPolicy(1, false)
        ));

        // Batch 포트폴리오 요약 템플릿 (여러 repo → 1회 AI 호출)
        // maxTokens: 단일 repo(4000)보다 넉넉히 설정 — 배열 응답이므로 repo 수에 비례
        // schemaFile: null — 배열 응답이라 기존 schema 재사용 불가, BatchPortfolioSummaryValidator가 직접 검증
        map.put("ai.portfolio.summary.batch.v1", new PromptTemplate(
            "ai.portfolio.summary.batch.v1", "v1", "portfolio_summary_batch",
            "system/common-system.txt",
            "developer/ai.portfolio.summary.batch.v1.txt",
            null,   // JSON Schema 파일 없음 (배열 검증은 BatchPortfolioSummaryValidator에서 수행)
            0.2, 8000, // 여러 repo의 배열 결과를 담으므로 단일 호출의 2배 확보
            new PromptTemplate.RetryPolicy(0, false)
        ));

        // 문제은행 연습 — CS 답변 평가
        // maxTokens: 한국어 feedback(3문장)+modelAnswer(3~5문장)가 영어 대비 토큰 2~3배 소비하므로 여유 확보
        map.put("ai.practice.evaluate.cs.v1", new PromptTemplate(
            "ai.practice.evaluate.cs.v1", "v1", "practice_evaluate_cs",
            "system/common-system.txt",
            "developer/ai.practice.evaluate.cs.v1.txt",
            "practice-evaluate.schema.json",
            0.3, 4000,
            new PromptTemplate.RetryPolicy(2, true)
        ));

        // 문제은행 연습 — 인성 답변 평가
        map.put("ai.practice.evaluate.behavioral.v1", new PromptTemplate(
            "ai.practice.evaluate.behavioral.v1", "v1", "practice_evaluate_behavioral",
            "system/common-system.txt",
            "developer/ai.practice.evaluate.behavioral.v1.txt",
            "practice-evaluate.schema.json",
            0.3, 4000,
            new PromptTemplate.RetryPolicy(2, true)
        ));

        // Map.copyOf()로 불변 맵 생성 — 이후 수정 시도 시 UnsupportedOperationException
        return new PromptTemplateRegistry(Map.copyOf(map));
    }
}
