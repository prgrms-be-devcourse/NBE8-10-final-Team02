package com.back.backend.domain.github.metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSanitizerTest {

    private TextSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new TextSanitizer();
    }

    // ─────────────────────────────────────────────────────────────────────
    // strip()
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("strip()")
    class Strip {

        @Test
        @DisplayName("코드 펜스 블록을 [code]로 치환한다")
        void strip_replacesCodeFenceWithPlaceholder() {
            String input = "설명\n```java\nSystem.out.println(\"hello\");\n```\n이후 설명";
            String result = sanitizer.strip(input);
            assertThat(result).contains("[code]");
            assertThat(result).doesNotContain("System.out.println");
        }

        @Test
        @DisplayName("인라인 코드를 [code]로 치환한다")
        void strip_replacesInlineCode() {
            String input = "변수 `myVariable`을 사용한다.";
            String result = sanitizer.strip(input);
            assertThat(result).contains("[code]");
            assertThat(result).doesNotContain("myVariable");
        }

        @Test
        @DisplayName("스택트레이스 라인을 제거한다")
        void strip_removesStacktraceLine() {
            String input = "Exception occurred\n\tat com.example.MyClass.method(MyClass.java:42)\n";
            String result = sanitizer.strip(input);
            assertThat(result).doesNotContain("at com.example.MyClass");
        }

        @Test
        @DisplayName("null 입력 시 빈 문자열 반환")
        void strip_returnsEmpty_whenNull() {
            assertThat(sanitizer.strip(null)).isEmpty();
        }

        @Test
        @DisplayName("코드 블록과 일반 텍스트가 혼재할 때 일반 텍스트는 보존")
        void strip_preservesNonCodeText() {
            String input = "성능 최적화를 위해\n```sql\nSELECT * FROM users;\n```\n인덱스를 추가했습니다.";
            String result = sanitizer.strip(input);
            assertThat(result).contains("성능 최적화");
            assertThat(result).contains("인덱스를 추가했습니다");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // removeTemplateNoise()
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeTemplateNoise()")
    class RemoveTemplateNoise {

        @Test
        @DisplayName("## Changes 섹션 헤더를 제거한다")
        void removeTemplateNoise_removesChangesHeader() {
            String input = "## Changes\n실제 변경 내용";
            String result = sanitizer.removeTemplateNoise(input);
            assertThat(result).doesNotContain("## Changes");
        }

        @Test
        @DisplayName("체크박스 항목을 제거한다")
        void removeTemplateNoise_removesCheckboxItems() {
            String input = "설명\n- [x] 테스트 완료\n- [ ] 문서 업데이트";
            String result = sanitizer.removeTemplateNoise(input);
            assertThat(result).doesNotContain("- [x]");
            assertThat(result).doesNotContain("- [ ]");
        }

        @Test
        @DisplayName("Resolves #번호 참조를 제거한다")
        void removeTemplateNoise_removesIssueReference() {
            String input = "> Resolves #123\n본문 내용";
            String result = sanitizer.removeTemplateNoise(input);
            assertThat(result).doesNotContain("Resolves");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // truncateAtSentence()
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("truncateAtSentence()")
    class TruncateAtSentence {

        @Test
        @DisplayName("maxLen 이하이면 원문 그대로 반환")
        void truncateAtSentence_returnsOriginal_whenWithinLimit() {
            String input = "짧은 텍스트입니다.";
            assertThat(sanitizer.truncateAtSentence(input, 500)).isEqualTo(input);
        }

        @Test
        @DisplayName("문장 끝 '. '에서 자른다")
        void truncateAtSentence_cutsAtSentenceBoundary() {
            // 70% 기준을 충족하는 위치에 문장 끝이 있도록 구성
            // maxLen=50, "." 위치가 50*0.7=35 이후에 있어야 문장 끝에서 자름
            String part1 = "이것은 첫 번째 문장입니다. ";   // 20자 (". " 포함)
            String filler = "두 번째 문장은 더 길고 상세합니다. ";  // 추가 내용
            String tail   = "매우 긴 세 번째 문장으로 이어집니다 끝이 없어서 잘려야 합니다 확인합니다.";
            String input = part1 + filler + tail;

            String result = sanitizer.truncateAtSentence(input, 60);

            // [truncated] 표시가 있어야 함
            assertThat(result).contains("[truncated]");
            // 원문보다 짧아야 함
            assertThat(result.length()).isLessThan(input.length());
        }

        @Test
        @DisplayName("문장 끝을 70% 이전에서만 찾으면 hard cut + ... [truncated]")
        void truncateAtSentence_hardCut_whenSentenceEndTooEarly() {
            // "." 가 맨 앞에만 있도록
            String input = "A. " + "B".repeat(200);
            String result = sanitizer.truncateAtSentence(input, 100);
            assertThat(result).endsWith("... [truncated]");
        }

        @Test
        @DisplayName("null 입력 시 빈 문자열 반환")
        void truncateAtSentence_returnsEmpty_whenNull() {
            assertThat(sanitizer.truncateAtSentence(null, 500)).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // extractPrBody()
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractPrBody()")
    class ExtractPrBody {

        @Test
        @DisplayName("maxLen 이하이면 원문 그대로 반환")
        void extractPrBody_returnsOriginal_whenWithinLimit() {
            String input = "짧은 PR 본문입니다.";
            assertThat(sanitizer.extractPrBody(input, 500)).isEqualTo(input);
        }

        @Test
        @DisplayName("기술 키워드 포함 문장을 우선 선택한다")
        void extractPrBody_selectsKeywordSentences() {
            // 키워드 문장을 중간에, 일반 문장을 앞뒤에 배치
            // head/tail 보너스(+5)가 없는 중간 위치에 있어도 키워드 점수로 선택되어야 함
            String keyword = "동시성 문제를 해결하기 위해 분산락을 도입하였습니다. ";
            // 일반 문장 10개로 앞뒤를 채워 head/tail 보너스 범위(앞3/뒤3) 밖으로 밀기
            String filler = "일반 변경 사항입니다. ".repeat(10);
            String input = filler + keyword + filler;

            String result = sanitizer.extractPrBody(input, 200);

            assertThat(result).contains("동시성");
        }

        @Test
        @DisplayName("긴 본문에서 [...] 생략 표시가 포함될 수 있다")
        void extractPrBody_mayIncludeOmissionMarker_whenSentencesSkipped() {
            // 매우 긴 본문 생성
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("이것은 ").append(i).append("번째 일반적인 문장입니다. ");
            }
            sb.append("동시성 트랜잭션 보안 최적화 핵심 내용. ");
            for (int i = 0; i < 5; i++) {
                sb.append("마지막 일반 문장 ").append(i).append(". ");
            }

            String result = sanitizer.extractPrBody(sb.toString(), 200);

            // 생략이 발생하면 [...] 포함, 없을 수도 있으므로 결과 길이만 확인
            assertThat(result.length()).isLessThanOrEqualTo(200 + 10); // 약간의 마진 허용
        }

        @Test
        @DisplayName("null 또는 blank 입력 시 빈 문자열 반환")
        void extractPrBody_returnsEmpty_whenNullOrBlank() {
            assertThat(sanitizer.extractPrBody(null, 500)).isEmpty();
            assertThat(sanitizer.extractPrBody("   ", 500)).isEmpty();
        }
    }
}
