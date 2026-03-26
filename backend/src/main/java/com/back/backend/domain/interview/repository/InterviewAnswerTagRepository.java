package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewAnswerTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewAnswerTagRepository extends JpaRepository<InterviewAnswerTag, Long> {

    @Query("""
            select answerTag
            from InterviewAnswerTag answerTag
            join fetch answerTag.answer answer
            join fetch answerTag.tag tag
            where answer.session.id = :sessionId
            order by answer.answerOrder asc, answerTag.id asc
            """)
    List<InterviewAnswerTag> findAllWithTagBySessionIdOrderByAnswerOrderAsc(@Param("sessionId") Long sessionId);
}
