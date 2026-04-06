package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewAnswer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {

    long countBySessionId(Long sessionId);

    List<InterviewAnswer> findAllBySessionIdOrderByAnswerOrderAsc(Long sessionId);

    @Query("""
            select answer
            from InterviewAnswer answer
            join fetch answer.sessionQuestion sessionQuestion
            where answer.session.id = :sessionId
            order by answer.answerOrder asc
            """)
    List<InterviewAnswer> findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(@Param("sessionId") Long sessionId);

    Optional<InterviewAnswer> findByIdAndSessionId(Long id, Long sessionId);

    @Query("""
            select answer
            from InterviewAnswer answer
            join fetch answer.sessionQuestion sessionQuestion
            left join fetch sessionQuestion.parentSessionQuestion parentSessionQuestion
            where answer.session.id = :sessionId
              and answer.followupResolvedAt is null
            order by answer.answerOrder desc
            """)
    List<InterviewAnswer> findAllPendingFollowupCandidatesBySessionId(
            @Param("sessionId") Long sessionId,
            Pageable pageable
    );

    @Query("""
            select kt.name, kt.category, avg(ia.score), count(ia)
            from InterviewAnswer ia
            join ia.sessionQuestion isq
            join isq.sourceQuestion iq
            join iq.knowledgeTags kt
            where ia.session.user.id = :userId
              and ia.score is not null
              and ia.skipped = false
            group by kt.name, kt.category
            order by avg(ia.score) asc
            """)
    List<Object[]> findWeakAreasByUserId(@Param("userId") Long userId);
}
