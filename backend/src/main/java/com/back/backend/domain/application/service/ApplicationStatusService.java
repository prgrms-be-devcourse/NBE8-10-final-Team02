package com.back.backend.domain.application.service;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.application.repository.ApplicationSourceRepositoryBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationStatusService {

    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicationSourceRepositoryBindingRepository applicationSourceRepositoryBindingRepository;
    private final ApplicationSourceDocumentBindingRepository applicationSourceDocumentBindingRepository;

    public boolean isReady(Application application) {
        return resolveStatus(application) == ApplicationStatus.READY;
    }

    public ApplicationStatus syncStatus(Application application) {
        ApplicationStatus resolvedStatus = resolveStatus(application);
        if (application.getStatus() != resolvedStatus) {
            application.changeStatus(resolvedStatus);
        }
        return resolvedStatus;
    }

    private ApplicationStatus resolveStatus(Application application) {
        Long applicationId = application.getId();
        if (applicationId == null) {
            return ApplicationStatus.DRAFT;
        }

        long sourceCount = applicationSourceRepositoryBindingRepository.countByApplicationId(applicationId)
                + applicationSourceDocumentBindingRepository.countByApplicationId(applicationId);

        if (sourceCount == 0) {
            return ApplicationStatus.DRAFT;
        }

        List<ApplicationQuestion> questions =
                applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId);
        if (questions.isEmpty()) {
            return ApplicationStatus.DRAFT;
        }

        return questions.stream().allMatch(this::hasUsableAnswer)
                ? ApplicationStatus.READY
                : ApplicationStatus.DRAFT;
    }

    private boolean hasUsableAnswer(ApplicationQuestion question) {
        return hasText(question.getEditedAnswer()) || hasText(question.getGeneratedAnswer());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
