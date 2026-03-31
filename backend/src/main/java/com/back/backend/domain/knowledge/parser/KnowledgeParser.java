package com.back.backend.domain.knowledge.parser;

import com.back.backend.domain.knowledge.source.KnowledgeSource;

import java.util.List;

public interface KnowledgeParser {

    List<ParsedItem> parse(String rawContent, KnowledgeSource source);

    record ParsedItem(
            String title,
            String content,
            List<String> autoTags
    ) {}
}
