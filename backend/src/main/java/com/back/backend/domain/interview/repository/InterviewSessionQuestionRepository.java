package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionQuestionRepository extends JpaRepository<InterviewSessionQuestion, Long> {

    long countBySessionId(Long sessionId);

    Optional<InterviewSessionQuestion> findByIdAndSessionId(Long id, Long sessionId);

    List<InterviewSessionQuestion> findAllBySessionIdOrderByQuestionOrderAsc(Long sessionId);

    @Query("""
            select sessionQuestion
            from InterviewSessionQuestion sessionQuestion
            where sessionQuestion.session.id = :sessionId
              and not exists (
                  select 1
                  from InterviewAnswer answer
                  where answer.session.id = :sessionId
                    and answer.sessionQuestion = sessionQuestion
              )
            order by sessionQuestion.questionOrder asc
            """)
    List<InterviewSessionQuestion> findAllUnansweredBySessionIdOrderByQuestionOrderAsc(
            @Param("sessionId") Long sessionId,
            Pageable pageable
    );

    boolean existsBySessionIdAndParentSessionQuestionId(Long sessionId, Long parentSessionQuestionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update interview_session_questions
            set question_order = question_order + :offset
            where session_id = :sessionId
              and question_order > :afterQuestionOrder
            """, nativeQuery = true)
    int addQuestionOrderOffsetAfter(
            @Param("sessionId") Long sessionId,
            @Param("afterQuestionOrder") int afterQuestionOrder,
            @Param("offset") int offset
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update interview_session_questions
            set question_order = question_order - :offsetDelta
            where session_id = :sessionId
              and question_order >= :fromQuestionOrder
            """, nativeQuery = true)
    int subtractQuestionOrderOffsetFrom(
            @Param("sessionId") Long sessionId,
            @Param("fromQuestionOrder") int fromQuestionOrder,
            @Param("offsetDelta") int offsetDelta
    );
}
