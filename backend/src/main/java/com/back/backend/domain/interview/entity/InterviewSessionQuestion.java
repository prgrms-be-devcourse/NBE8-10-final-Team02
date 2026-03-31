package com.back.backend.domain.interview.entity;

import com.back.backend.global.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
        name = "interview_session_questions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_interview_session_questions_order", columnNames = {"session_id", "question_order"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewSessionQuestion extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_question_id")
    private InterviewQuestion sourceQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_session_question_id")
    private InterviewSessionQuestion parentSessionQuestion;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Convert(converter = InterviewQuestionTypeConverter.class)
    @Column(name = "question_type", nullable = false, length = 30)
    private InterviewQuestionType questionType;

    @Convert(converter = DifficultyLevelConverter.class)
    @Column(name = "difficulty_level", nullable = false, length = 20)
    private DifficultyLevel difficultyLevel;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    public void changeQuestionOrder(int questionOrder) {
        this.questionOrder = questionOrder;
    }

    public void changeParentSessionQuestion(InterviewSessionQuestion parentSessionQuestion) {
        this.parentSessionQuestion = parentSessionQuestion;
    }
}
