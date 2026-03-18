package com.back.backend.interview.entity;

import com.back.backend.global.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@Table(name = "feedback_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FeedbackTag extends CreatedAtEntity {

    @Column(name = "tag_name", nullable = false, unique = true, length = 100)
    private String tagName;

    @Convert(converter = FeedbackTagCategoryConverter.class)
    @Column(name = "tag_category", nullable = false, length = 20)
    private FeedbackTagCategory tagCategory;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}
