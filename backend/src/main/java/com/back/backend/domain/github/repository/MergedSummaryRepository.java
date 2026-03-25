package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.MergedSummary;
import com.back.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MergedSummaryRepository extends JpaRepository<MergedSummary, Long> {

    // 최신 버전 조회 (면접/자소서 생성 시 사용)
    Optional<MergedSummary> findTopByUserOrderByMergedVersionDesc(User user);
}
