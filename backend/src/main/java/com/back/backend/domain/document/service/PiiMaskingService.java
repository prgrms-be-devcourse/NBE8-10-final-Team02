package com.back.backend.domain.document.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 업로드 문서에서 추출한 텍스트의 개인식별정보(PII)를 마스킹하는 서비스.
 *
 * <p>마스킹 포맷은 SensitiveMasker(https://github.com/Sangmoo/SensitiveMasker) 참고:</p>
 * <ul>
 *   <li>이름: 홍길동 → 홍*동 (첫+끝 보존)</li>
 *   <li>주민번호: 900101-1234567 → 900101-1****** (뒷자리 첫 숫자만)</li>
 *   <li>전화번호: 010-1234-5678 → 010-****-5678 (가운데 마스킹)</li>
 *   <li>이메일: abc@domain.com → a**@domain.com (첫 글자만 보존)</li>
 *   <li>여권번호: M12345678 → M*******8 (첫+끝 보존)</li>
 *   <li>주소: 상세 번지 부분 마스킹</li>
 * </ul>
 *
 * <p>정규식 기반 커스텀 구현이며, 각 패턴은 enum 순서대로 적용된다.
 * 구조가 명확한 패턴(주민번호, 전화번호 등)을 먼저 처리하고,
 * 이름 패턴은 마지막에 적용해 false positive를 줄인다.</p>
 */
@Service
public class PiiMaskingService {

    /**
     * PII 패턴 정의. enum 순서가 적용 우선순위이다.
     * 구조가 명확한 패턴을 먼저 배치해 false positive를 줄인다.
     */
    private enum PiiPattern {
        // 주민등록번호: 6자리-7자리 (하이픈/공백 선택)
        RESIDENT_REG(Pattern.compile(
            "\\d{6}[\\s-]?[1-4]\\d{6}")),

        // 전화번호: 010-1234-5678 또는 01012345678
        PHONE(Pattern.compile(
            "01[0-9][\\s.-]?\\d{3,4}[\\s.-]?\\d{4}")),

        // 이메일: user@domain.com
        EMAIL(Pattern.compile(
            "[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}")),

        // 여권번호: 알파벳 1~2자리 + 숫자 7~8자리
        PASSPORT(Pattern.compile(
            "(?<![A-Z])[A-Z]{1,2}\\d{7,8}(?!\\d)")),

        // 생년월일: 19xx 또는 20xx 형식
        BIRTHDATE(Pattern.compile(
            "(19|20)\\d{2}[./-]\\d{2}[./-]\\d{2}")),

        // 주소: 시도명 + 로/길/동 + 번지
        ADDRESS(Pattern.compile(
            "(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)"
            + "[\\s\\S]{0,30}(로|길|동|읍|면|리)\\s*\\d+")),

        // 이름 — 레이블 기반 (높은 정확도, 먼저 적용)
        LABEL_NAME(Pattern.compile(
            "(?<=(이름|성명|지원자|Name|Applicant)[\\s:：]{0,3})[가-힣]{2,4}")),

        // 이름 — 컨텍스트 기반 (LABEL_NAME이 못 잡은 것 보조)
        KOREAN_NAME(Pattern.compile(
            "(?:"
                + "(이름|성명|지원자|담당자|작성자|신청자|대표자|계약자|보호자|수신인|발신인|성함)"
                + "|(?i)(Name|Full\\s*Name|Applicant|Author|Candidate|"
                + "Prepared\\s*by|Submitted\\s*by)"
                + ")[\\s:：|\\-~∙•·\\t]{0,8}"
                + "([가-힣]{2,4}|[A-Z][a-z]+(?:\\s[A-Z][a-z]+){1,2})",
            Pattern.MULTILINE));

        final Pattern pattern;

        PiiPattern(Pattern p) {
            this.pattern = p;
        }
    }

    /**
     * 텍스트에서 PII를 탐지하고 마스킹된 텍스트를 반환한다.
     *
     * <p>이미 마스킹된 영역을 추적해 KOREAN_NAME이 LABEL_NAME과 중복 처리하지 않도록 한다.</p>
     *
     * @param text 원본 텍스트 (null 허용)
     * @return 마스킹된 텍스트, null 입력 시 null 반환
     */
    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        Set<String> alreadyMasked = new HashSet<>();

        for (PiiPattern pii : PiiPattern.values()) {
            Matcher matcher = pii.pattern.matcher(result);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String matched = matcher.group();

                // KOREAN_NAME이 이미 마스킹된 영역을 재처리하지 않도록
                if (pii == PiiPattern.KOREAN_NAME && alreadyMasked.contains(matched)) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matched));
                    continue;
                }

                String masked = maskByType(pii, matched);
                if (pii == PiiPattern.LABEL_NAME) {
                    alreadyMasked.add(matched);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    /**
     * PII 유형별 마스킹 포맷을 적용한다.
     * SensitiveMasker 포맷 참고: https://github.com/Sangmoo/SensitiveMasker
     */
    private String maskByType(PiiPattern type, String value) {
        return switch (type) {
            // 주민번호: 900101-1234567 → 900101-1******
            case RESIDENT_REG -> maskResidentReg(value);
            // 전화번호: 010-1234-5678 → 010-****-5678
            case PHONE -> maskPhone(value);
            // 이메일: abc@domain.com → a**@domain.com
            case EMAIL -> maskEmail(value);
            // 여권번호: M12345678 → M*******8 (첫+끝 보존)
            case PASSPORT -> maskFirstLast(value);
            // 생년월일: 1990/01/01 → 1990/**/01
            case BIRTHDATE -> maskBirthdate(value);
            // 주소: 상세 번지 부분을 *** 로 대체
            case ADDRESS -> maskAddress(value);
            // 이름: 홍길동 → 홍*동 (첫+끝 보존)
            case LABEL_NAME, KOREAN_NAME -> maskName(value);
        };
    }

    /** 주민번호: 앞 7자리(6+하이픈+1) 보존, 나머지 ****** */
    private String maskResidentReg(String value) {
        // 하이픈 유무 처리: "900101-1234567" 또는 "9001011234567"
        String digits = value.replaceAll("[\\s-]", "");
        if (digits.length() == 13) {
            return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
        }
        return value.substring(0, Math.min(7, value.length()))
            + "*".repeat(Math.max(0, value.length() - 7));
    }

    /** 전화번호: 가운데 마스킹 — 010-****-5678 */
    private String maskPhone(String value) {
        // 하이픈/점/공백이 있는 경우
        String cleaned = value.replaceAll("[\\s.-]", "");
        if (cleaned.length() >= 10) {
            String prefix = cleaned.substring(0, 3);
            String suffix = cleaned.substring(cleaned.length() - 4);
            return prefix + "-****-" + suffix;
        }
        return value;
    }

    /** 이메일: 첫 글자 보존 — a**@domain.com, 1글자면 *@domain.com */
    private String maskEmail(String value) {
        int atIdx = value.indexOf('@');
        if (atIdx <= 0) {
            return value;
        }
        if (atIdx == 1) {
            return "*" + value.substring(atIdx);
        }
        return value.charAt(0) + "*".repeat(atIdx - 1) + value.substring(atIdx);
    }

    /** 여권번호 등: 첫+끝 보존, 가운데 마스킹 — M*******8 */
    private String maskFirstLast(String value) {
        if (value.length() <= 2) {
            return value;
        }
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    // 생년월일: 연도 보존, 월일 마스킹 (1990/01/01 → 1990/*/*)
    private String maskBirthdate(String value) {
        // 구분자(./- 등) 찾아서 연도만 보존
        char separator = '/';
        for (char c : value.toCharArray()) {
            if (c == '.' || c == '/' || c == '-') {
                separator = c;
                break;
            }
        }
        String[] parts = value.split("[./-]");
        if (parts.length == 3) {
            return parts[0] + separator + "**" + separator + "**";
        }
        return maskFirstLast(value);
    }

    /**
     * 주소: 번지 이후를 *** 로 마스킹.
     * 정규식이 "시도명...로/길/동 + 번지숫자"를 매치하므로,
     * 매치된 문자열에서 마지막 숫자 부분을 ***로 대체한다.
     */
    private String maskAddress(String value) {
        // 마지막 숫자 부분을 ***로 대체
        return value.replaceAll("\\d+$", "***");
    }

    /**
     * 이름: 첫+끝 보존, 가운데 마스킹 — 홍*동, 남**수
     * 2글자일 때: 이* (첫 글자만 보존)
     */
    private String maskName(String value) {
        if (value.length() <= 1) {
            return value;
        }
        if (value.length() == 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }
}
