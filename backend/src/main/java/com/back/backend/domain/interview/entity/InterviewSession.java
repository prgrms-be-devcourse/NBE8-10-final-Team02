package com.back.backend.domain.interview.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import com.back.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Builder
@Table(name = "interview_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_set_id", nullable = false)
    private InterviewQuestionSet questionSet;

    @Convert(converter = InterviewSessionStatusConverter.class)
    @Column(name = "status", nullable = false, length = 30)
    private InterviewSessionStatus status;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "summary_feedback", columnDefinition = "text")
    private String summaryFeedback;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public void changeStatus(InterviewSessionStatus status) {
        this.status = status;
    }

    public void changeLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public void changeEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public void applyResult(int totalScore, String summaryFeedback) {
        this.totalScore = totalScore;
        this.summaryFeedback = summaryFeedback;
    }
}
