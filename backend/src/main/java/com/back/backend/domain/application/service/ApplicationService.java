package com.back.backend.domain.application.service;

import com.back.backend.domain.application.dto.request.CreateApplicationRequest;
import com.back.backend.domain.application.dto.request.SaveApplicationQuestionsRequest;
import com.back.backend.domain.application.dto.request.SaveApplicationSourcesRequest;
import com.back.backend.domain.application.dto.request.UpdateApplicationRequest;
import com.back.backend.domain.application.dto.response.ApplicationQuestionResponse;
import com.back.backend.domain.application.dto.response.ApplicationResponse;
import com.back.backend.domain.application.dto.response.ApplicationSourceBindingResponse;
import com.back.backend.domain.application.mapper.ApplicationResponseMapper;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.application.repository.ApplicationSourceRepositoryBindingRepository;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationLengthOption;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.entity.ApplicationSourceRepository;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.entity.ApplicationToneOption;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.repository.GithubRepositoryQueryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.response.FieldErrorDetail;
import com.back.backend.domain.activity.event.ApplicationReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicationSourceRepositoryBindingRepository applicationSourceRepositoryBindingRepository;
    private final ApplicationSourceDocumentBindingRepository applicationSourceDocumentBindingRepository;
    private final DocumentRepository documentRepository;
    private final GithubRepositoryQueryRepository githubRepositoryQueryRepository;
    private final UserRepository userRepository;
    private final ApplicationResponseMapper applicationResponseMapper;
    private final ApplicationStatusService applicationStatusService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApplicationResponse createApplication(long userId, CreateApplicationRequest request) {
        User user = getUser(userId);
        String jobRole = requireJobRole(request.jobRole());

        Application application = Application.builder()
                .user(user)
                .applicationTitle(normalizeOptionalText(request.applicationTitle()))
                .companyName(normalizeOptionalText(request.companyName()))
                .applicationType(normalizeOptionalText(request.applicationType()))
                .jobRole(jobRole)
                .status(ApplicationStatus.DRAFT)
                .build();

        applicationRepository.save(application);
        return applicationResponseMapper.toApplicationResponse(application);
    }

    @Transactional
    public List<ApplicationResponse> getApplications(long userId) {
        List<Application> applications = applicationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        applications.forEach(applicationStatusService::syncStatus);
        return applicationResponseMapper.toApplicationResponses(applications);
    }

    @Transactional
    public ApplicationResponse getApplication(long userId, long applicationId) {
        Application application = getOwnedApplication(userId, applicationId);
        applicationStatusService.syncStatus(application);
        return applicationResponseMapper.toApplicationResponse(application);
    }

    @Transactional
    public ApplicationResponse updateApplication(long userId, long applicationId, UpdateApplicationRequest request) {
        Application application = getOwnedApplication(userId, applicationId);

        String jobRole = request.jobRole() == null
                ? application.getJobRole()
                : requireJobRole(request.jobRole());

        application.updateBasics(
                request.applicationTitle() == null
                        ? application.getApplicationTitle()
                        : normalizeOptionalText(request.applicationTitle()),
                request.companyName() == null
                        ? application.getCompanyName()
                        : normalizeOptionalText(request.companyName()),
                request.applicationType() == null
                        ? application.getApplicationType()
                        : normalizeOptionalText(request.applicationType()),
                jobRole
        );

        if (request.status() != null) {
            ApplicationStatus requestedStatus = parseEnum(
                    request.status(),
                    ApplicationStatus.class,
                    "status"
            );

            // ready는 명시적으로 요청했을 때만 판정하고, 기준 미달이면 상태 승격을 막는다.
            if (requestedStatus == ApplicationStatus.READY && !applicationStatusService.isReady(application)) {
                throw new ServiceException(
                        ErrorCode.APPLICATION_STATUS_CONFLICT,
                        HttpStatus.CONFLICT,
                        "지원 단위를 ready 상태로 변경할 수 없습니다."
                );
            }

            application.changeStatus(requestedStatus);

            if (requestedStatus == ApplicationStatus.READY) {
                eventPublisher.publishEvent(
                        new ApplicationReadyEvent(application.getUser().getId(), application.getId()));
            }
        }

        return applicationResponseMapper.toApplicationResponse(application);
    }

    @Transactional
    public void deleteApplication(long userId, long applicationId) {
        applicationRepository.delete(getOwnedApplication(userId, applicationId));
    }

    @Transactional
    public ApplicationSourceBindingResponse saveSources(
            long userId,
            long applicationId,
            SaveApplicationSourcesRequest request
    ) {
        Application application = getOwnedApplication(userId, applicationId);

        List<Long> repositoryIds = normalizeIds(request.repositoryIdsOrEmpty(), "repositoryIds");
        List<Long> documentIds = normalizeIds(request.documentIdsOrEmpty(), "documentIds");

        List<GithubRepository> repositories = repositoryIds.isEmpty()
                ? List.of()
                : githubRepositoryQueryRepository.findAllOwnedByIds(userId, repositoryIds);
        List<Document> documents = documentIds.isEmpty()
                ? List.of()
                : documentRepository.findAllByIdInAndUserId(documentIds, userId);

        validateOwnedSourceCount(repositoryIds, repositories.size(), ErrorCode.GITHUB_REPOSITORY_NOT_FOUND, "선택한 repository를 찾을 수 없습니다.");
        validateOwnedSourceCount(documentIds, documents.size(), ErrorCode.DOCUMENT_NOT_FOUND, "선택한 문서를 찾을 수 없습니다.");

        // source 저장은 부분 수정이 아니라 전체 교체로 다루며, 같은 키 재삽입 전에 삭제를 먼저 반영한다.
        applicationSourceRepositoryBindingRepository.deleteByApplicationId(applicationId);
        applicationSourceDocumentBindingRepository.deleteByApplicationId(applicationId);
        applicationSourceRepositoryBindingRepository.flush();
        applicationSourceDocumentBindingRepository.flush();

        applicationSourceRepositoryBindingRepository.saveAll(
                repositories.stream()
                        .map(repository -> ApplicationSourceRepository.builder()
                                .application(application)
                                .repository(repository)
                                .build())
                        .toList()
        );
        applicationSourceDocumentBindingRepository.saveAll(
                documents.stream()
                        .map(document -> ApplicationSourceDocument.builder()
                                .application(application)
                                .document(document)
                                .build())
                        .toList()
        );

        applicationStatusService.syncStatus(application);

        return applicationResponseMapper.toApplicationSourceBindingResponse(
                applicationId,
                repositoryIds,
                documentIds
        );
    }

    @Transactional
    public List<ApplicationQuestionResponse> saveQuestions(
            long userId,
            long applicationId,
            SaveApplicationQuestionsRequest request
    ) {
        Application application = getOwnedApplication(userId, applicationId);
        List<SaveApplicationQuestionsRequest.QuestionItem> questionItems = request.questionsOrEmpty();

        validateQuestions(questionItems);
        // 문항 저장도 화면 기준 현재 목록 전체를 덮어쓰는 방식으로 맞추고, 기존 순번 삭제를 먼저 반영한다.
        applicationQuestionRepository.deleteByApplicationId(applicationId);
        applicationQuestionRepository.flush();

        List<ApplicationQuestion> questions = questionItems.stream()
                .map(questionItem -> ApplicationQuestion.builder()
                        .application(application)
                        .questionOrder(questionItem.questionOrder())
                        .questionText(questionItem.questionText().trim())
                        .generatedAnswer(null)
                        .editedAnswer(null)
                        .toneOption(parseNullableEnum(
                                questionItem.toneOption(),
                                ApplicationToneOption.class,
                                "questions.toneOption"
                        ))
                        .lengthOption(parseNullableEnum(
                                questionItem.lengthOption(),
                                ApplicationLengthOption.class,
                                "questions.lengthOption"
                        ))
                        .emphasisPoint(normalizeOptionalText(questionItem.emphasisPoint()))
                        .build())
                .toList();

        List<ApplicationQuestion> savedQuestions = applicationQuestionRepository.saveAll(questions);
        applicationStatusService.syncStatus(application);

        return applicationResponseMapper.toApplicationQuestionResponses(
                savedQuestions.stream()
                        .sorted((left, right) -> Integer.compare(left.getQuestionOrder(), right.getQuestionOrder()))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<ApplicationQuestionResponse> getQuestions(long userId, long applicationId) {
        getOwnedApplication(userId, applicationId);
        return applicationResponseMapper.toApplicationQuestionResponses(
                applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(applicationId)
        );
    }

    private User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "사용자를 찾을 수 없습니다."
                ));
    }

    private Application getOwnedApplication(long userId, long applicationId) {
        return applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.APPLICATION_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "지원 준비를 찾을 수 없습니다."
                ));
    }

    private void validateOwnedSourceCount(
            List<Long> requestedIds,
            int ownedCount,
            ErrorCode errorCode,
            String message
    ) {
        if (requestedIds.size() != ownedCount) {
            throw new ServiceException(errorCode, HttpStatus.NOT_FOUND, message);
        }
    }

    private void validateQuestions(List<SaveApplicationQuestionsRequest.QuestionItem> questionItems) {
        if (questionItems.isEmpty()) {
            throw new ServiceException(
                    ErrorCode.APPLICATION_QUESTION_REQUIRED,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "자소서 문항을 1개 이상 입력해주세요."
            );
        }

        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        Set<Integer> questionOrders = new LinkedHashSet<>();

        if (questionItems.size() > 10) {
            fieldErrors.add(new FieldErrorDetail("questions", "max_10"));
        }

        for (SaveApplicationQuestionsRequest.QuestionItem questionItem : questionItems) {
            if (questionItem.questionOrder() == null || questionItem.questionOrder() <= 0) {
                fieldErrors.add(new FieldErrorDetail("questions.questionOrder", "invalid"));
            } else if (!questionOrders.add(questionItem.questionOrder())) {
                fieldErrors.add(new FieldErrorDetail("questions.questionOrder", "duplicate"));
            }

            if (questionItem.questionText() == null || questionItem.questionText().isBlank()) {
                fieldErrors.add(new FieldErrorDetail("questions.questionText", "required"));
            }

            validateOptionalEnum(questionItem.toneOption(), ApplicationToneOption.class, "questions.toneOption", fieldErrors);
            validateOptionalEnum(questionItem.lengthOption(), ApplicationLengthOption.class, "questions.lengthOption", fieldErrors);
        }

        if (!fieldErrors.isEmpty()) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    fieldErrors
            );
        }
    }

    private List<Long> normalizeIds(List<Long> ids, String field) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();

        for (Long id : ids) {
            if (id == null || id <= 0) {
                fieldErrors.add(new FieldErrorDetail(field, "invalid"));
                continue;
            }
            normalizedIds.add(id);
        }

        if (!fieldErrors.isEmpty()) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    fieldErrors
            );
        }

        return List.copyOf(normalizedIds);
    }

    private String requireJobRole(String jobRole) {
        String normalized = normalizeOptionalText(jobRole);
        if (normalized == null) {
            throw new ServiceException(
                    ErrorCode.APPLICATION_JOB_ROLE_REQUIRED,
                    HttpStatus.BAD_REQUEST,
                    "직무를 입력해주세요.",
                    false,
                    List.of(new FieldErrorDetail("jobRole", "required"))
            );
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private <E extends Enum<E> & StringCodeEnum> E parseNullableEnum(
            String rawValue,
            Class<E> enumType,
            String field
    ) {
        String normalized = normalizeOptionalText(rawValue);
        if (normalized == null) {
            return null;
        }
        return parseEnum(normalized, enumType, field);
    }

    private <E extends Enum<E> & StringCodeEnum> E parseEnum(
            String rawValue,
            Class<E> enumType,
            String field
    ) {
        return Arrays.stream(enumType.getEnumConstants())
                .filter(candidate -> candidate.getValue().equals(rawValue))
                .findFirst()
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.REQUEST_VALIDATION_FAILED,
                        HttpStatus.BAD_REQUEST,
                        "요청 값을 다시 확인해주세요.",
                        false,
                        List.of(new FieldErrorDetail(field, "invalid"))
                ));
    }

    private <E extends Enum<E> & StringCodeEnum> void validateOptionalEnum(
            String rawValue,
            Class<E> enumType,
            String field,
            List<FieldErrorDetail> fieldErrors
    ) {
        String normalized = normalizeOptionalText(rawValue);
        if (normalized == null) {
            return;
        }

        boolean supported = Arrays.stream(enumType.getEnumConstants())
                .anyMatch(candidate -> candidate.getValue().equals(normalized));

        if (!supported) {
            fieldErrors.add(new FieldErrorDetail(field, "invalid"));
        }
    }
}
