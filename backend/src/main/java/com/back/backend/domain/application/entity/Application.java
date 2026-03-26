package com.back.backend.domain.application.entity;

import com.back.backend.global.jpa.entity.BaseTimeEntity;
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

    @Column(name = "application_title", length = 255)
    private String applicationTitle;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "application_type", length = 100)
    private String applicationType;

    @Column(name = "job_role", nullable = false, length = 255)
    private String jobRole;

    @Convert(converter = ApplicationStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status;

    public void updateBasics(String applicationTitle, String companyName, String applicationType, String jobRole) {
        this.applicationTitle = applicationTitle;
        this.companyName = companyName;
        this.applicationType = applicationType;
        this.jobRole = jobRole;
    }

    public void changeStatus(ApplicationStatus status) {
        this.status = status;
    }
}
