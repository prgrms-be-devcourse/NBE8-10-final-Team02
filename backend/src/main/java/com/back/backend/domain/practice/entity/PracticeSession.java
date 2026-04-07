package com.back.backend.domain.practice.entity;

import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
@Table(
        name = "practice_sessions",
        indexes = {
                @Index(name = "idx_practice_sessions_user", columnList = "user_id, created_at desc"),
                @Index(name = "idx_practice_sessions_user_type", columnList = "user_id, question_type, created_at desc")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PracticeSession extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_item_id", nullable = false)
    private KnowledgeItem knowledgeItem;

    @Convert(converter = PracticeQuestionTypeConverter.class)
    @Column(name = "question_type", nullable = false, length = 20)
    private PracticeQuestionType questionType;

    @Convert(converter = PracticeSessionStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private PracticeSessionStatus status;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(name = "score")
    private Integer score;

    @Column(name = "feedback", columnDefinition = "text")
    private String feedback;

    @Column(name = "model_answer", columnDefinition = "text")
    private String modelAnswer;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    public static PracticeSession create(User user, KnowledgeItem knowledgeItem,
                                         PracticeQuestionType questionType, String answerText) {
        return PracticeSession.builder()
                .user(user)
                .knowledgeItem(knowledgeItem)
                .questionType(questionType)
                .status(PracticeSessionStatus.IN_PROGRESS)
                .answerText(answerText)
                .build();
    }

    public void applyEvaluation(int score, String feedback, String modelAnswer) {
        this.score = score;
        this.feedback = feedback;
        this.modelAnswer = modelAnswer;
        this.status = PracticeSessionStatus.EVALUATED;
        this.evaluatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = PracticeSessionStatus.FAILED;
    }
}
