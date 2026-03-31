package com.back.backend.domain.interview.entity;

import com.back.backend.global.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.Check;

@Getter
@Entity
@Builder
@Check(constraints = "is_skipped or nullif(btrim(answer_text), '') is not null")
@Table(
        name = "interview_answers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_interview_answers_session_question", columnNames = {"session_id", "session_question_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewAnswer extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_question_id", nullable = false)
    private InterviewSessionQuestion sessionQuestion;

    @Column(name = "answer_order", nullable = false)
    private Integer answerOrder;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(name = "is_skipped", nullable = false)
    private boolean skipped;

    @Column(name = "score")
    private Integer score;

    @Column(name = "evaluation_rationale", columnDefinition = "text")
    private String evaluationRationale;

    public void applyEvaluation(int score, String evaluationRationale) {
        this.score = score;
        this.evaluationRationale = evaluationRationale;
    }
}
