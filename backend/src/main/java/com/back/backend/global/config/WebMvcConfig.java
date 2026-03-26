package com.back.backend.global.config;

import com.back.backend.global.web.converter.StringCodeEnumConverterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 공통 설정.
 *
 * <p>{@link StringCodeEnumConverterFactory}를 등록해 HTTP 파라미터로 전달된
 * lowercase code 값(예: {@code "other"})을 {@code StringCodeEnum} 구현 enum으로
 * 자동 변환한다.</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new StringCodeEnumConverterFactory());
    }
}
