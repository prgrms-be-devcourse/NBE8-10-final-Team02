package com.back.backend.domain.knowledge.parser;

import com.back.backend.domain.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonBehavioralParserTest {

    private final JsonBehavioralParser parser = new JsonBehavioralParser();
    private final KnowledgeSource source = new KnowledgeSource.LocalFileSource(
            "local-behavioral",
            "data/behavioral-questions.json",
            parser
    );

    @Test
    void parse_createsItemsFromVariationsAndAddsBehavioralTag() {
        String raw = """
                [
                  {
                    "id": "B_001",
                    "category": "communication",
                    "ai_prompt_guide": "STAR 구조로 답변하도록 유도",
                    "variations": [
                      { "tone": "standard", "question": "협업 중 갈등을 해결한 경험을 설명해 주세요." },
                      { "tone": "pressure", "question": "왜 그때 더 빨리 조치하지 못했나요?" }
                    ]
                  }
                ]
                """;

        List<KnowledgeParser.ParsedItem> items = parser.parse(raw, source);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("B_001 [standard]");
        assertThat(items.get(0).content()).contains("협업 중 갈등", "[AI Guide]");
        assertThat(items.get(0).autoTags()).contains("behavioral");
    }

    @Test
    void parse_throwsWhenJsonIsMalformed() {
        assertThatThrownBy(() -> parser.parse("{not-json", source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("behavioral-questions.json 파싱 실패");
    }

    @Test
    void parse_returnsEmptyWhenEntriesAreInvalidOrArrayIsEmpty() {
        String raw = """
                [
                  {
                    "id": "",
                    "category": "basics",
                    "variations": [
                      { "tone": "standard", "question": "  " }
                    ]
                  }
                ]
                """;

        assertThat(parser.parse("[]", source)).isEmpty();
        assertThat(parser.parse(raw, source)).isEmpty();
    }
}
