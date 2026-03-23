package com.back.backend.domain.application.mapper;

import com.back.backend.domain.application.dto.response.ApplicationQuestionResponse;
import com.back.backend.domain.application.dto.response.ApplicationResponse;
import com.back.backend.domain.application.dto.response.ApplicationSourceBindingResponse;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApplicationResponseMapper {

    public ApplicationResponse toApplicationResponse(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getApplicationTitle(),
                application.getCompanyName(),
                application.getJobRole(),
                application.getStatus().getValue(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.getApplicationType()
        );
    }

    public List<ApplicationResponse> toApplicationResponses(List<Application> applications) {
        return applications.stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    public ApplicationQuestionResponse toApplicationQuestionResponse(ApplicationQuestion question) {
        return new ApplicationQuestionResponse(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionText(),
                question.getGeneratedAnswer(),
                question.getEditedAnswer(),
                question.getToneOption() == null ? null : question.getToneOption().getValue(),
                question.getLengthOption() == null ? null : question.getLengthOption().getValue(),
                question.getEmphasisPoint()
        );
    }

    public List<ApplicationQuestionResponse> toApplicationQuestionResponses(List<ApplicationQuestion> questions) {
        return questions.stream()
                .map(this::toApplicationQuestionResponse)
                .toList();
    }

    public ApplicationSourceBindingResponse toApplicationSourceBindingResponse(
            Long applicationId,
            List<Long> repositoryIds,
            List<Long> documentIds
    ) {
        return new ApplicationSourceBindingResponse(
                applicationId,
                repositoryIds,
                documentIds,
                repositoryIds.size() + documentIds.size()
        );
    }
}
