package com.back.backend.interview.entity;

import com.back.backend.application.entity.ApplicationQuestion;
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
        name = "interview_questions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_interview_questions_order", columnNames = {"question_set_id", "question_order"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewQuestion extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_set_id", nullable = false)
    private InterviewQuestionSet questionSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_question_id")
    private InterviewQuestion parentQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_application_question_id")
    private ApplicationQuestion sourceApplicationQuestion;

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
}
