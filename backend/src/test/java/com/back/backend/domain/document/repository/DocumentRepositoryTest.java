package com.back.backend.domain.document.repository;


import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.support.IntegrationTest;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// [Integration Test 계층]
// - @IntegrationTest: SpringBootTest + Testcontainers 공통 설정
// - @Transactional: 각 테스트 후 롤백으로 격리
@IntegrationTest
@Transactional
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private EntityManager em;

    @Test
    void countByUserId_returnsZeroForNewUser() {
        User user = persistUser("new@example.com");

        assertThat(documentRepository.countByUserId(user.getId())).isZero();
    }

    @Test
    void countByUserId_returnsCorrectCountAfterUploads() {
        User user = persistUser("uploader@example.com");
        em.persist(documentOf(user, "resume.pdf"));
        em.persist(documentOf(user, "award.pdf"));
        em.flush();

        assertThat(documentRepository.countByUserId(user.getId())).isEqualTo(2);
    }

    @Test
    void countByUserId_doesNotCountOtherUsersDocuments() {
        User owner = persistUser("owner@example.com");
        User other = persistUser("other@example.com");
        em.persist(documentOf(owner, "resume.pdf"));
        em.flush();

        assertThat(documentRepository.countByUserId(other.getId())).isZero();
    }

    private User persistUser(String email) {
        User user = User.builder()
                .email(email)
                .displayName("tester")
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(user);
        return user;
    }

    private Document documentOf(User user, String fileName) {
        return Document.builder()
                .user(user)
                .documentType(DocumentType.RESUME)
                .originalFileName(fileName)
                .storagePath("uploads/" + fileName)
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .extractStatus(DocumentExtractStatus.PENDING)
                .uploadedAt(Instant.now())
                .build();
    }
}
