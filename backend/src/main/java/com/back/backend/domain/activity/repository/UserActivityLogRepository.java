package com.back.backend.domain.activity.repository;

import com.back.backend.domain.activity.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    Optional<UserActivityLog> findByUserIdAndActivityDate(Long userId, LocalDate activityDate);

    List<UserActivityLog> findAllByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
            Long userId, LocalDate from, LocalDate to);
}
