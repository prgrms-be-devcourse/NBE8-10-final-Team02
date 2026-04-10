package com.back.backend.domain.knowledge.parser;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordTagExtractorTest {

    @Test
    void extract_returnsConfiguredTagsWithoutDuplicates() {
        List<String> tags = KeywordTagExtractor.extract(
                "Java backend interview question with JUnit testing and Spring Boot integration test"
        );

        assertThat(tags).contains("java", "backend", "interview", "testing", "spring");
        assertThat(new HashSet<>(tags)).hasSize(tags.size());
    }

    @Test
    void categoryOf_returnsConfiguredCategoryOrTopicFallback() {
        assertThat(KeywordTagExtractor.categoryOf("java")).isEqualTo("language");
        assertThat(KeywordTagExtractor.categoryOf("backend")).isEqualTo("domain");
        assertThat(KeywordTagExtractor.categoryOf("unknown-tag")).isEqualTo("topic");
    }
}
