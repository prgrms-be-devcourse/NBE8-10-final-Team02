package com.back.backend.domain.practice.dto.response;

import com.back.backend.domain.knowledge.entity.KnowledgeTag;

public record PracticeTagResponse(
        Long id,
        String name,
        String category
) {

    public static PracticeTagResponse from(KnowledgeTag tag) {
        return new PracticeTagResponse(tag.getId(), tag.getName(), tag.getCategory());
    }
}
