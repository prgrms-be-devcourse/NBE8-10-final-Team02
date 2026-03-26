package com.back.backend.global.web.converter;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.Arrays;

/**
 * {@link StringCodeEnum}을 구현하는 enum을 HTTP 요청 파라미터에서 변환하는 팩토리.
 *
 * <p>Spring MVC의 기본 enum 변환은 {@code Enum.valueOf()} (대소문자 구분 상수명)를 사용하지만,
 * 이 팩토리는 {@code getValue()} 반환값(lowercase code)으로도 매칭한다.
 * 예: {@code "other"} → {@code DocumentType.OTHER}
 *
 * <p>매칭 우선순위:
 * <ol>
 *   <li>getValue() 일치 (소문자 코드값)</li>
 *   <li>name() 대소문자 무시 일치 (이전 호환성)</li>
 * </ol>
 */
public class StringCodeEnumConverterFactory
        implements ConverterFactory<String, Enum<?>> {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Enum<?>> Converter<String, T> getConverter(Class<T> targetType) {
        if (!StringCodeEnum.class.isAssignableFrom(targetType)) {
            return null;
        }
        return source -> {
            if (source == null || source.isBlank()) {
                return null;
            }
            T[] constants = targetType.getEnumConstants();
            // 1) getValue() 일치
            return (T) Arrays.stream(constants)
                    .filter(c -> ((StringCodeEnum) c).getValue().equals(source))
                    .findFirst()
                    // 2) name() 대소문자 무시
                    .orElseGet(() -> Arrays.stream(constants)
                            .filter(c -> c.name().equalsIgnoreCase(source))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Cannot convert '" + source + "' to " + targetType.getSimpleName())));
        };
    }
}
