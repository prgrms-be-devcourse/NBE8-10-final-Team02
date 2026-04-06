package com.back.backend.domain.activity.entity;

import com.back.backend.domain.user.entity.User;
import com.back.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "user_activity_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserActivityLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "activity_count", nullable = false)
    private int activityCount;

    public UserActivityLog(User user, LocalDate activityDate) {
        this.user = user;
        this.activityDate = activityDate;
        this.activityCount = 1;
    }

    public void increment() {
        this.activityCount++;
    }
}
