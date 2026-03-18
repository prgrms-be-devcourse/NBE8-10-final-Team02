package com.back.backend.application.entity;

import com.back.backend.global.jpa.entity.BaseTimeEntity;
import com.back.backend.user.entity.User;
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

@Getter
@Entity
@Builder
@Table(name = "applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Application extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "application_title")
    private String applicationTitle;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "application_type")
    private String applicationType;

    @Column(name = "job_role", nullable = false)
    private String jobRole;

    @Convert(converter = ApplicationStatusConverter.class)
    @Column(name = "status", nullable = false)
    private ApplicationStatus status;
}
