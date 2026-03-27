package com.back.backend.domain.application.service;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.application.repository.ApplicationSourceRepositoryBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApplicationStatusServiceTest {

    private static final long APPLICATION_ID = 100L;

    @Mock
    private ApplicationQuestionRepository applicationQuestionRepository;

    @Mock
    private ApplicationSourceRepositoryBindingRepository applicationSourceRepositoryBindingRepository;

    @Mock
    private ApplicationSourceDocumentBindingRepository applicationSourceDocumentBindingRepository;

    private ApplicationStatusService applicationStatusService;

    @BeforeEach
    void setUp() {
        applicationStatusService = new ApplicationStatusService(
                applicationQuestionRepository,
                applicationSourceRepositoryBindingRepository,
                applicationSourceDocumentBindingRepository
        );
    }

    @Test
    @DisplayName("source와 문항별 usable answer가 모두 있으면 ready로 동기화한다")
    void syncStatus_promotesReadyWhenRequirementsAreMet() {
        Application application = application(ApplicationStatus.DRAFT);

        given(applicationSourceRepositoryBindingRepository.countByApplicationId(APPLICATION_ID)).willReturn(1L);
        given(applicationSourceDocumentBindingRepository.countByApplicationId(APPLICATION_ID)).willReturn(0L);
        given(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(APPLICATION_ID))
                .willReturn(List.of(answeredQuestion("생성 답변"), answeredQuestion("수정 답변")));

        ApplicationStatus resolved = applicationStatusService.syncStatus(application);

        assertThat(resolved).isEqualTo(ApplicationStatus.READY);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.READY);
    }

    @Test
    @DisplayName("source가 없거나 문항 답변이 비어 있으면 draft로 동기화한다")
    void syncStatus_downgradesDraftWhenRequirementsAreMissing() {
        Application application = application(ApplicationStatus.READY);

        given(applicationSourceRepositoryBindingRepository.countByApplicationId(APPLICATION_ID)).willReturn(0L);
        given(applicationSourceDocumentBindingRepository.countByApplicationId(APPLICATION_ID)).willReturn(0L);

        ApplicationStatus resolved = applicationStatusService.syncStatus(application);

        assertThat(resolved).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(applicationStatusService.isReady(application)).isFalse();
    }

    private Application application(ApplicationStatus status) {
        Application application = Application.builder()
                .jobRole("Backend Engineer")
                .status(status)
                .build();
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        return application;
    }

    private ApplicationQuestion answeredQuestion(String answerText) {
        return ApplicationQuestion.builder()
                .generatedAnswer(answerText)
                .editedAnswer(answerText)
                .build();
    }

    private ApplicationQuestion unansweredQuestion() {
        return ApplicationQuestion.builder()
                .generatedAnswer(null)
                .editedAnswer(null)
                .build();
    }
}
