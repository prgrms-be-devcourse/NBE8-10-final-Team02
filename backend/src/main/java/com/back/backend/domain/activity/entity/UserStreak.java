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

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_streaks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStreak extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "last_active_date")
    private LocalDate lastActiveDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserStreak(User user) {
        this.user = user;
        this.currentStreak = 0;
        this.longestStreak = 0;
        this.updatedAt = Instant.now();
    }

    public void recordActivity(LocalDate todayKst) {
        if (lastActiveDate == null) {
            currentStreak = 1;
        } else if (lastActiveDate.equals(todayKst)) {
            return;
        } else if (lastActiveDate.equals(todayKst.minusDays(1))) {
            currentStreak++;
        } else {
            currentStreak = 1;
        }

        longestStreak = Math.max(longestStreak, currentStreak);
        lastActiveDate = todayKst;
        updatedAt = Instant.now();
    }

    public int getEffectiveStreak(LocalDate todayKst) {
        if (lastActiveDate == null) {
            return 0;
        }
        if (lastActiveDate.equals(todayKst) || lastActiveDate.equals(todayKst.minusDays(1))) {
            return currentStreak;
        }
        return 0;
    }
}
