package com.back.backend.domain.interview.entity;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.global.jpa.entity.CreatedAtEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Builder
@Table(name = "interview_question_sets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InterviewQuestionSet extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Convert(converter = DifficultyLevelConverter.class)
    @Column(name = "difficulty_level", nullable = false, length = 20)
    private DifficultyLevel difficultyLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "question_types", columnDefinition = "text[]")
    private String[] questionTypes;

    public void changeQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }
}
