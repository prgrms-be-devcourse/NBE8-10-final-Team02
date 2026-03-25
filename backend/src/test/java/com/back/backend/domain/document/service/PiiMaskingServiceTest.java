package com.back.backend.domain.document.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PiiMaskingService 단위 테스트.
 *
 * <p>Spring context 없이 실행되며, 각 PII 패턴별 마스킹 정상 경로와
 * 경계 조건(null, 빈 문자열, 이중 마스킹 방지)을 검증한다.</p>
 *
 * <p>마스킹 포맷은 SensitiveMasker(https://github.com/Sangmoo/SensitiveMasker) 참고.</p>
 */
class PiiMaskingServiceTest {

    private PiiMaskingService piiMaskingService;

    @BeforeEach
    void setUp() {
        piiMaskingService = new PiiMaskingService();
    }

    // =========================================================
    // null / empty 안전성
    // =========================================================

    @Test
    void mask_returnsNullWhenInputIsNull() {
        assertThat(piiMaskingService.mask(null)).isNull();
    }

    @Test
    void mask_returnsEmptyWhenInputIsEmpty() {
        assertThat(piiMaskingService.mask("")).isEmpty();
    }

    // =========================================================
    // 주민등록번호: 900101-1234567 → 900101-1******
    // =========================================================

    @Test
    void mask_residentRegistrationNumber() {
        String input = "주민번호 900101-1234567 입니다";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("900101-1******");
        assertThat(result).doesNotContain("1234567");
    }

    @Test
    void mask_residentRegistrationNumberWithoutHyphen() {
        String input = "주민번호 9001011234567 입니다";
        String result = piiMaskingService.mask(input);

        // 하이픈 없는 형태도 마스킹
        assertThat(result).doesNotContain("9001011234567");
    }

    // =========================================================
    // 전화번호: 010-1234-5678 → 010-****-5678
    // =========================================================

    @Test
    void mask_phoneNumber() {
        String input = "연락처 010-1234-5678 입니다";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("010-****-5678");
        assertThat(result).doesNotContain("1234");
    }

    @Test
    void mask_phoneNumberWithoutHyphen() {
        String input = "전화 01012345678";
        String result = piiMaskingService.mask(input);

        assertThat(result).doesNotContain("01012345678");
    }

    @Test
    void mask_phoneWithParenthesis() {
        String input = "연락처 010)1234-5678";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("010-****-5678");
        assertThat(result).doesNotContain("1234");
    }

    @Test
    void mask_phoneWithFullParenthesis() {
        String input = "전화 (02)1234-5678";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("02-****-5678");
    }

    @Test
    void mask_seoulLandline() {
        String input = "전화 02-1234-5678";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("02-****-5678");
        assertThat(result).doesNotContain("02-1234");
    }

    @Test
    void mask_regionalLandline() {
        String input = "연락처 031-123-4567";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("031-****-4567");
        assertThat(result).doesNotContain("031-123");
    }

    @Test
    void mask_internetPhone() {
        String input = "전화 070-1234-5678";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("070-****-5678");
    }

    @Test
    void mask_representativeNumber() {
        String input = "고객센터 1588-1234";
        String result = piiMaskingService.mask(input);

        assertThat(result).doesNotContain("1588-1234");
    }

    // =========================================================
    // 이메일: abc@domain.com → a**@domain.com
    // =========================================================

    @Test
    void mask_email() {
        String input = "이메일 abc@domain.com 입니다";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("a**@domain.com");
        assertThat(result).doesNotContain("abc@");
    }

    @Test
    void mask_emailSingleCharLocal() {
        // 로컬 파트가 1글자인 경우: a@domain.com → *@domain.com
        String input = "메일 a@domain.com";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("*@domain.com");
    }

    // =========================================================
    // 여권번호: M12345678 → M*******8
    // =========================================================

    @Test
    void mask_passport() {
        String input = "여권 M12345678 번호입니다";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("M*******8");
        assertThat(result).doesNotContain("M12345678");
    }

    // =========================================================
    // 생년월일: 1990/01/01 → 부분 마스킹
    // =========================================================

    @Test
    void mask_birthdate() {
        String input = "생년월일 1990/01/01 입니다";
        String result = piiMaskingService.mask(input);

        assertThat(result).doesNotContain("1990/01/01");
    }

    @Test
    void mask_birthdateWithHyphen() {
        String input = "생년월일 1990-01-01";
        String result = piiMaskingService.mask(input);

        assertThat(result).doesNotContain("1990-01-01");
    }

    // =========================================================
    // 주소: 서울시 강남구 테헤란로 123 → 서울시 강남구 테헤란로 ***
    // =========================================================

    @Test
    void mask_address() {
        String input = "주소 서울시 강남구 테헤란로 123";
        String result = piiMaskingService.mask(input);

        // 상세 번지가 마스킹되어야 함
        assertThat(result).doesNotContain("테헤란로 123");
    }

    // =========================================================
    // 이름 (레이블 기반): 이름: 홍길동 → 이름: 홍*동
    // =========================================================

    @Test
    void mask_labeledName() {
        String input = "이름: 홍길동";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("홍*동");
        assertThat(result).doesNotContain("홍길동");
    }

    @Test
    void mask_labeledNameWithFourChars() {
        String input = "성명: 남궁민수";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("남**수");
        assertThat(result).doesNotContain("남궁민수");
    }

    @Test
    void mask_labeledNameTwoChars() {
        String input = "이름: 이몽";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("이*");
        assertThat(result).doesNotContain("이몽");
    }

    // =========================================================
    // PII 없음 → 원문 그대로
    // =========================================================

    @Test
    void mask_noMatch_returnsOriginal() {
        String input = "이것은 PII가 없는 일반 텍스트입니다.";
        String result = piiMaskingService.mask(input);

        assertThat(result).isEqualTo(input);
    }

    // =========================================================
    // 복합 PII → 모두 마스킹
    // =========================================================

    @Test
    void mask_multiplePii() {
        String input = "이름: 홍길동\n연락처: 010-1234-5678\n이메일: hong@test.com";
        String result = piiMaskingService.mask(input);

        assertThat(result).contains("홍*동");
        assertThat(result).contains("010-****-5678");
        assertThat(result).contains("h***@test.com");
        assertThat(result).doesNotContain("홍길동");
        assertThat(result).doesNotContain("010-1234-5678");
        assertThat(result).doesNotContain("hong@");
    }

    // =========================================================
    // 이중 마스킹 방지 — LABEL_NAME이 잡은 이름을 KOREAN_NAME이 재처리하지 않음
    // =========================================================

    @Test
    void mask_alreadyMaskedNotDoubled() {
        String input = "이름: 홍길동";
        String result = piiMaskingService.mask(input);

        // "홍*동"이 한 번만 나와야 하며 "홍**" 같은 이중 마스킹이 아님
        assertThat(result).contains("홍*동");
        // 마스킹 결과에 "***" 같은 과도한 마스킹이 없어야 함
        assertThat(result).doesNotContain("*****");
    }
}
