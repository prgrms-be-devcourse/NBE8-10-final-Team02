package com.back.backend.domain.interview.support;

import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import jakarta.persistence.EntityManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InterviewSessionQuestionTestHelper {

    private InterviewSessionQuestionTestHelper() {
    }

    public static List<InterviewSessionQuestion> persistSessionQuestionSnapshot(
            EntityManager entityManager,
            InterviewSession session
    ) {
        List<InterviewQuestion> sourceQuestions = entityManager.createQuery(
                        "select q from InterviewQuestion q where q.questionSet.id = :questionSetId order by q.questionOrder asc",
                        InterviewQuestion.class
                )
                .setParameter("questionSetId", session.getQuestionSet().getId())
                .getResultList();

        Map<Long, InterviewSessionQuestion> sessionQuestionsBySourceId = new LinkedHashMap<>();
        List<InterviewSessionQuestion> sessionQuestions = sourceQuestions.stream()
                .map(sourceQuestion -> {
                    InterviewSessionQuestion sessionQuestion = InterviewSessionQuestion.builder()
                            .session(session)
                            .sourceQuestion(sourceQuestion)
                            .questionOrder(sourceQuestion.getQuestionOrder())
                            .questionType(sourceQuestion.getQuestionType())
                            .difficultyLevel(sourceQuestion.getDifficultyLevel())
                            .questionText(sourceQuestion.getQuestionText())
                            .build();
                    entityManager.persist(sessionQuestion);
                    sessionQuestionsBySourceId.put(sourceQuestion.getId(), sessionQuestion);
                    return sessionQuestion;
                })
                .toList();

        entityManager.flush();

        for (InterviewQuestion sourceQuestion : sourceQuestions) {
            if (sourceQuestion.getParentQuestion() == null) {
                continue;
            }

            InterviewSessionQuestion childQuestion = sessionQuestionsBySourceId.get(sourceQuestion.getId());
            InterviewSessionQuestion parentQuestion = sessionQuestionsBySourceId.get(sourceQuestion.getParentQuestion().getId());
            childQuestion.changeParentSessionQuestion(parentQuestion);
        }

        entityManager.flush();
        return sessionQuestions;
    }

    public static InterviewSessionQuestion findSessionQuestion(
            EntityManager entityManager,
            InterviewSession session,
            int questionOrder
    ) {
        return entityManager.createQuery(
                        "select q from InterviewSessionQuestion q where q.session.id = :sessionId and q.questionOrder = :questionOrder",
                        InterviewSessionQuestion.class
                )
                .setParameter("sessionId", session.getId())
                .setParameter("questionOrder", questionOrder)
                .getSingleResult();
    }
}
