package com.back.backend.ai.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateRegistryTest {

    @Test
    @DisplayName("createDefault()로 6개 템플릿이 모두 등록된다")
    void createDefault_registers6Templates() {
        PromptTemplateRegistry registry = PromptTemplateRegistry.createDefault();

        assertThat(registry.get("ai.portfolio.summary.v1")).isNotNull();
        assertThat(registry.get("ai.self_intro.generate.v1")).isNotNull();
        assertThat(registry.get("ai.interview.questions.generate.v1")).isNotNull();
        assertThat(registry.get("ai.interview.followup.generate.v1")).isNotNull();
        assertThat(registry.get("ai.interview.evaluate.v1")).isNotNull();
        assertThat(registry.get("ai.interview.summary.v1")).isNotNull();
    }

    @Test
    @DisplayName("템플릿의 설정값이 docs 기준과 일치한다")
    void createDefault_correctValues() {
        PromptTemplateRegistry registry = PromptTemplateRegistry.createDefault();

        PromptTemplate portfolio = registry.get("ai.portfolio.summary.v1");
        PromptTemplate followup = registry.get("ai.interview.followup.generate.v1");

        assertThat(portfolio.temperature()).isEqualTo(0.2);
        assertThat(followup.temperature()).isEqualTo(0.5);

        assertThat(portfolio.retryPolicy().maxRetries()).isEqualTo(2);
        assertThat(followup.retryPolicy().maxRetries()).isEqualTo(1);

        assertThat(portfolio.retryPolicy().allowFallback()).isFalse();
    }

    @Test
    @DisplayName("템플릿의 프롬프트 파일 경로가 올바르다")
    void createDefault_correctFilePaths() {
        PromptTemplateRegistry registry = PromptTemplateRegistry.createDefault();

        PromptTemplate template = registry.get("ai.portfolio.summary.v1");

        assertThat(template.systemPromptFile()).isEqualTo("system/common-system.txt");
        assertThat(template.developerPromptFile()).isEqualTo("developer/ai.portfolio.summary.v1.txt");
        assertThat(template.schemaFile()).isEqualTo("schema/portfolio-summary.schema.json");
    }

    @Test
    @DisplayName("등록되지 않은 templateId 조회 시 예외를 던진다")
    void get_unknownTemplateId() {
        PromptTemplateRegistry registry = PromptTemplateRegistry.createDefault();

        assertThatThrownBy(() -> registry.get("ai.nonexistent.v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("등록되지 않은 templateId");
    }

    @Test
    @DisplayName("PromptTemplate은 record로 불변이 보장된다")
    void promptTemplate_isImmutableRecord() {
        PromptTemplateRegistry registry = PromptTemplateRegistry.createDefault();

        PromptTemplate template = registry.get("ai.portfolio.summary.v1");

        assertThat(template).isInstanceOf(Record.class);
    }

    @Test
    @DisplayName("PromptTemplate 생성 시 필수값 누락하면 예외를 던진다")
    void promptTemplate_validation() {
        // templateId null
        assertThatThrownBy(() -> new PromptTemplate(
            null, "v1", "test",
            "system/common-system.txt",
            "developer/test.txt",
            "schema/test.schema.json",
            0.5, 1000,
            new PromptTemplate.RetryPolicy(1, false)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateId");

        // temperature 범위 초과
        assertThatThrownBy(() -> new PromptTemplate(
            "test.v1", "v1", "test",
            "system/common-system.txt",
            "developer/test.txt",
            "schema/test.schema.json",
            1.5, 1000,
            new PromptTemplate.RetryPolicy(1, false)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("temperature");

        // maxTokens 0
        assertThatThrownBy(() -> new PromptTemplate(
            "test.v1", "v1", "test",
            "system/common-system.txt",
            "developer/test.txt",
            "schema/test.schema.json",
            0.5, 0,
            new PromptTemplate.RetryPolicy(1, false)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxTokens");
    }
}
