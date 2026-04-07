package com.back.backend.domain.practice.entity;

import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.global.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@Table(
        name = "practice_session_tags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_practice_session_tags", columnNames = {"practice_session_id", "feedback_tag_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PracticeSessionTag extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_session_id", nullable = false)
    private PracticeSession practiceSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_tag_id", nullable = false)
    private FeedbackTag feedbackTag;
}
