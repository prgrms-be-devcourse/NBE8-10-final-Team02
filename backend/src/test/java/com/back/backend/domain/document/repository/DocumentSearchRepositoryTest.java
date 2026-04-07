package com.back.backend.domain.document.repository;

import com.back.backend.config.PGroongaTestcontainersConfiguration;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PGroonga 전문 검색(FTS) 통합 테스트.
 *
 * <p>PGroonga가 설치된 PostgreSQL 컨테이너({@link PGroongaTestcontainersConfiguration})를 사용해
 * {@code &@~} 연산자 기반 검색이 실제로 동작하는지 검증한다.</p>
 *
 * <p>일반 {@code @IntegrationTest}와 달리 PGroonga 이미지를 사용하므로
 * Docker Hub에서 이미지를 처음 pull하는 경우 시간이 걸릴 수 있다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PGroongaTestcontainersConfiguration.class)
@Transactional
class DocumentSearchRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private EntityManager em;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = persistUser("searcher@example.com");
        otherUser = persistUser("other@example.com");
    }

    // =========================================================
    // 텍스트 검색 (extractedText 기준)
    // =========================================================

    @Test
    void searchByText_returnsMatchingDocument() {
        persistDocument(user, "이력서.pdf", "Java Spring Boot 백엔드 개발자입니다. MSA 경험 3년.");
        em.flush();

        List<Document> results = documentRepository.searchByText(user.getId(), "Spring Boot");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOriginalFileName()).isEqualTo("이력서.pdf");
    }

    @Test
    void searchByText_returnsEmptyWhenNoMatch() {
        persistDocument(user, "이력서.pdf", "Java 백엔드 개발자.");
        em.flush();

        List<Document> results = documentRepository.searchByText(user.getId(), "Python");

        assertThat(results).isEmpty();
    }

    @Test
    void searchByText_doesNotReturnOtherUsersDocuments() {
        // 다른 사용자의 문서에 검색어가 포함되어 있어도 반환하지 않는다
        persistDocument(otherUser, "타인이력서.pdf", "Spring Boot 전문가");
        persistDocument(user, "내이력서.pdf", "Java 개발자");
        em.flush();

        List<Document> results = documentRepository.searchByText(user.getId(), "Spring Boot");

        assertThat(results).isEmpty();
    }

    @Test
    void searchByText_returnsOnlySuccessStatusDocuments() {
        // PENDING 상태 문서는 검색에서 제외
        Document doc = Document.builder()
            .user(user)
            .documentType(DocumentType.RESUME)
            .originalFileName("pending.pdf")
            .storagePath("uploads/pending.pdf")
            .mimeType("application/pdf")
            .fileSizeBytes(1024L)
            .extractStatus(DocumentExtractStatus.PENDING) // ← PENDING, extractedText null
            .uploadedAt(Instant.now())
            .build();
        em.persist(doc);
        em.flush();

        List<Document> results = documentRepository.searchByText(user.getId(), "Spring Boot");

        assertThat(results).isEmpty();
    }

    @Test
    void searchByText_handlesKoreanQuery() {
        persistDocument(user, "이력서.pdf", "백엔드 개발자 포트폴리오. Spring Boot, JPA, Redis 활용.");
        em.flush();

        List<Document> results = documentRepository.searchByText(user.getId(), "백엔드");

        assertThat(results).hasSize(1);
    }

    // =========================================================
    // 파일명 검색 (originalFileName 기준)
    // =========================================================

    @Test
    void searchByFileName_returnsMatchingDocument() {
        persistDocument(user, "resume_2026.pdf", "경력기술서 내용");
        persistDocument(user, "portfolio_design.pdf", "포트폴리오 내용");
        em.flush();

        List<Document> results = documentRepository.searchByFileName(user.getId(), "resume");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOriginalFileName()).isEqualTo("resume_2026.pdf");
    }

    @Test
    void searchByFileName_doesNotReturnOtherUsersDocuments() {
        persistDocument(otherUser, "resume.pdf", "내용");
        em.flush();

        List<Document> results = documentRepository.searchByFileName(user.getId(), "resume");

        assertThat(results).isEmpty();
    }

    // =========================================================
    // Test helpers
    // =========================================================

    private User persistUser(String email) {
        User u = User.builder()
            .email(email)
            .displayName("tester")
            .status(UserStatus.ACTIVE)
            .build();
        em.persist(u);
        return u;
    }

    private Document persistDocument(User owner, String fileName, String extractedText) {
        Document doc = Document.builder()
            .user(owner)
            .documentType(DocumentType.RESUME)
            .originalFileName(fileName)
            .storagePath("uploads/" + fileName)
            .mimeType("application/pdf")
            .fileSizeBytes(1024L)
            .extractStatus(DocumentExtractStatus.SUCCESS)
            .uploadedAt(Instant.now())
            .build();
        doc.markExtracted(extractedText, Instant.now());
        em.persist(doc);
        return doc;
    }
}
