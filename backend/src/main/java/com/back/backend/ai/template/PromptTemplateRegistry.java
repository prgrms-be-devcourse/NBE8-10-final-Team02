package com.back.backend.ai.template;

import java.util.HashMap;
import java.util.Map;

/**
 * 프롬프트 템플릿 레지스트리
 * 6개 템플릿을 등록하고 templateId로 조회
 */
public class PromptTemplateRegistry {

    private final Map<String, PromptTemplate> templates = new HashMap<>();

    public void register(PromptTemplate template) {
        if (templates.containsKey(template.templateId())) {
            throw new IllegalArgumentException("이미 등록된 templateId: " + template.templateId());
        }
        templates.put(template.templateId(), template);
    }

    public PromptTemplate get(String templateId) {
        PromptTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("등록되지 않은 templateId: " + templateId);
        }
        return template;
    }

    /**
     * 6개 기본 템플릿이 모두 등록된 레지스트리를 생성
     */
    public static PromptTemplateRegistry createDefault() {
        PromptTemplateRegistry registry = new PromptTemplateRegistry();

        registry.register(new PromptTemplate(
            "ai.portfolio.summary.v1", "v1", "portfolio_summary",
            "system/common-system.txt",
            "developer/ai.portfolio.summary.v1.txt",
            "schema/portfolio-summary.schema.json",
            0.2, 2000,
            new PromptTemplate.RetryPolicy(2, false)
        ));

        registry.register(new PromptTemplate(
            "ai.self_intro.generate.v1", "v1", "self_intro_generation",
            "system/common-system.txt",
            "developer/ai.self_intro.generate.v1.txt",
            "schema/self-intro-generate.schema.json",
            0.5, 4000,
            new PromptTemplate.RetryPolicy(2, false)
        ));

        registry.register(new PromptTemplate(
            "ai.interview.questions.generate.v1", "v1", "interview_question_generation",
            "system/common-system.txt",
            "developer/ai.interview.questions.generate.v1.txt",
            "schema/interview-questions-generate.schema.json",
            0.6, 4000,
            new PromptTemplate.RetryPolicy(2, false)
        ));

        registry.register(new PromptTemplate(
            "ai.interview.followup.generate.v1", "v1", "interview_followup_generation",
            "system/common-system.txt",
            "developer/ai.interview.followup.generate.v1.txt",
            "schema/interview-followup-generate.schema.json",
            0.5, 1000,
            new PromptTemplate.RetryPolicy(1, false)
        ));

        registry.register(new PromptTemplate(
            "ai.interview.evaluate.v1", "v1", "interview_evaluation",
            "system/common-system.txt",
            "developer/ai.interview.evaluate.v1.txt",
            "schema/interview-evaluate.schema.json",
            0.2, 2500,
            new PromptTemplate.RetryPolicy(2, false)
        ));

        registry.register(new PromptTemplate(
            "ai.interview.summary.v1", "v1", "interview_summary",
            "system/common-system.txt",
            "developer/ai.interview.summary.v1.txt",
            "schema/interview-summary.schema.json",
            0.3, 1500,
            new PromptTemplate.RetryPolicy(1, false)
        ));

        return registry;
    }
}
