package com.back.backend.domain.activity.service;

import com.back.backend.domain.activity.dto.ActivityEntryDto;
import com.back.backend.domain.activity.dto.StreakResponse;
import com.back.backend.domain.activity.repository.UserActivityLogRepository;
import com.back.backend.domain.activity.repository.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserStreakRepository userStreakRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    public StreakResponse getStreak(Long userId) {
        LocalDate todayKst = LocalDate.now(KST);
        return userStreakRepository.findByUserId(userId)
                .map(streak -> new StreakResponse(
                        streak.getEffectiveStreak(todayKst),
                        streak.getLongestStreak()))
                .orElse(new StreakResponse(0, 0));
    }

    public List<ActivityEntryDto> getHeatmap(Long userId, int days) {
        LocalDate todayKst = LocalDate.now(KST);
        LocalDate fromDate = todayKst.minusDays(days - 1);

        return userActivityLogRepository
                .findAllByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, fromDate, todayKst)
                .stream()
                .map(log -> new ActivityEntryDto(log.getActivityDate().toString(), log.getActivityCount()))
                .toList();
    }
}
