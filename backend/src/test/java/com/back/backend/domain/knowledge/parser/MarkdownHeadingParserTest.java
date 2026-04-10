package com.back.backend.domain.knowledge.parser;

import com.back.backend.domain.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownHeadingParserTest {

    private final MarkdownHeadingParser parser = new MarkdownHeadingParser();
    private final KnowledgeSource source = new KnowledgeSource.GithubSource(
            "test",
            "owner/repo",
            List.of("README.md"),
            parser
    );

    @Test
    void parse_extractsHeadingsAndStripsLinksImagesAndReferences() {
        String raw = """
                ## JVM Memory
                Java [official docs](https://example.com) 와 ![diagram](https://example.com/a.png) 이미지를 제외하고도
                충분히 긴 설명을 남겨야 합니다. Garbage Collection, Heap, Stack, testing, backend 내용을 함께 적습니다.

                ### 참고 자료
                이 섹션은 결과에서 제거되어야 합니다.

                ## Spring Transaction
                Spring Boot JPA transaction propagation 과 테스트 전략을 설명하는 충분히 긴 본문입니다.
                Mock 과 integration test 차이를 함께 적어서 태그 추출도 확인합니다.
                """;

        List<KnowledgeParser.ParsedItem> items = parser.parse(raw, source);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("JVM Memory");
        assertThat(items.get(0).content()).contains("official docs").doesNotContain("![diagram]", "참고 자료");
        assertThat(items.get(0).autoTags()).contains("java", "backend");
        assertThat(items.get(1).title()).isEqualTo("Spring Transaction");
    }

    @Test
    void parse_ignoresShortOrMissingSections() {
        String raw = """
                ## Too Short
                짧다.

                그냥 일반 문장만 있습니다.
                """;

        List<KnowledgeParser.ParsedItem> items = parser.parse(raw, source);

        assertThat(items).isEmpty();
    }
}
