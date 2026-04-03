package com.back.backend.domain.activity.service;

import com.back.backend.domain.activity.dto.ActivityEntryDto;
import com.back.backend.domain.activity.dto.ActivityStatsResponse;
import com.back.backend.domain.activity.dto.ScoreTrendEntry;
import com.back.backend.domain.activity.dto.StreakResponse;
import com.back.backend.domain.activity.dto.WeakAreaEntry;
import com.back.backend.domain.activity.repository.UserActivityLogRepository;
import com.back.backend.domain.activity.repository.UserStreakRepository;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
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
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;

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

    public ActivityStatsResponse getStats(Long userId) {
        List<ScoreTrendEntry> scoreTrend = interviewSessionRepository
                .findAllByUserIdAndStatusOrderByEndedAtAsc(userId, InterviewSessionStatus.FEEDBACK_COMPLETED)
                .stream()
                .filter(s -> s.getTotalScore() != null)
                .map(s -> new ScoreTrendEntry(s.getId(), s.getTotalScore(), s.getEndedAt().toString()))
                .toList();

        List<WeakAreaEntry> weakAreas = interviewAnswerRepository
                .findWeakAreasByUserId(userId)
                .stream()
                .map(row -> new WeakAreaEntry(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).intValue()
                ))
                .toList();

        return new ActivityStatsResponse(scoreTrend, weakAreas);
    }
}
