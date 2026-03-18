package com.back.backend.application.entity;

import com.back.backend.global.jpa.entity.BaseTimeEntity;
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
        name = "application_questions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_application_questions_order", columnNames = {"application_id", "question_order"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationQuestion extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "generated_answer", columnDefinition = "text")
    private String generatedAnswer;

    @Column(name = "edited_answer", columnDefinition = "text")
    private String editedAnswer;

    @Convert(converter = ApplicationToneOptionConverter.class)
    @Column(name = "tone_option", length = 20)
    private ApplicationToneOption toneOption;

    @Convert(converter = ApplicationLengthOptionConverter.class)
    @Column(name = "length_option", length = 20)
    private ApplicationLengthOption lengthOption;

    @Column(name = "emphasis_point", length = 255)
    private String emphasisPoint;
}
