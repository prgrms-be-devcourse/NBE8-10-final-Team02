package com.back.backend.domain.practice.repository;

import com.back.backend.domain.practice.entity.PracticeSessionTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PracticeSessionTagRepository extends JpaRepository<PracticeSessionTag, Long> {

    @Query("""
            select pst
            from PracticeSessionTag pst
            join fetch pst.feedbackTag
            where pst.practiceSession.id = :sessionId
            order by pst.id asc
            """)
    List<PracticeSessionTag> findAllWithTagBySessionId(@Param("sessionId") Long sessionId);
}
