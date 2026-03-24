package com.back.backend.domain.ai.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private PromptLoader promptLoader;

    @BeforeEach
    void setUp() {
        promptLoader = new PromptLoader();
    }

    @Test
    @DisplayName("존재하는 파일을 로딩하면 내용을 반환한다")
    void load_existingFile_returnsContent() {
        String content = promptLoader.load("system/common-system.txt");

        assertThat(content).isNotNull();
        assertThat(content).isNotBlank();
    }

    @Test
    @DisplayName("존재하지 않는 파일 로딩 시 예외를 던진다")
    void load_nonExistentFile_throwsException() {
        assertThatThrownBy(() -> promptLoader.load("system/nonexistent.txt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("프롬프트 파일을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("같은 파일을 두 번 로딩하면 동일한 인스턴스를 반환한다 (캐시 동작)")
    void load_samePath_returnsCachedInstance() {
        String first = promptLoader.load("system/common-system.txt");
        String second = promptLoader.load("system/common-system.txt");

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("6개 developer 프롬프트 파일을 모두 로딩할 수 있다")
    void load_allDeveloperPromptFiles() {
        assertThat(promptLoader.load("developer/ai.portfolio.summary.v1.txt")).isNotBlank();
        assertThat(promptLoader.load("developer/ai.self_intro.generate.v1.txt")).isNotBlank();
        assertThat(promptLoader.load("developer/ai.interview.questions.generate.v1.txt")).isNotBlank();
        assertThat(promptLoader.load("developer/ai.interview.followup.generate.v1.txt")).isNotBlank();
        assertThat(promptLoader.load("developer/ai.interview.evaluate.v1.txt")).isNotBlank();
        assertThat(promptLoader.load("developer/ai.interview.summary.v1.txt")).isNotBlank();
    }
}
