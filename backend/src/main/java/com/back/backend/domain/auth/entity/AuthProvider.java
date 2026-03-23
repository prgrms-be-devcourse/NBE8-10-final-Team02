package com.back.backend.domain.auth.entity;

import com.back.backend.global.jpa.converter.StringCodeEnum;
import com.back.backend.global.jpa.converter.StringCodeEnumConverter;
import jakarta.persistence.Converter;

/**
 * 서비스에서 지원하는 소셜 로그인 공급자(OAuth2 Provider) 목록입니다.
 * <p>
 * StringCodeEnum을 구현하여 DB 저장 시 단순 문자열이 아닌
 * 내부적으로 정의된 고유 코드값(value)을 사용하도록 설계되었습니다.
 */
public enum AuthProvider implements StringCodeEnum {
    GITHUB("github"),
    GOOGLE("google"),
    KAKAO("kakao");

    private final String value;

    AuthProvider(String value) {
        this.value = value;
    }

    /**
     * DB 및 컨버터에서 참조할 실제 코드값을 반환합니다.
     */
    @Override
    public String getValue() {
        return value;
    }

    /**
     * OAuth2 설정(registrationId) 문자열을 기반으로 해당하는 Enum 상수를 찾아 반환합니다.
     * * @param registrationId 소셜 로그인 식별자 (예: "github", "google")
     * @return 일치하는 AuthProvider 상수
     * @throws IllegalArgumentException 지원하지 않는 공급자일 경우 발생
     */
    public static AuthProvider fromRegistrationId(String registrationId) {
        for (AuthProvider provider : values()) {
            if (provider.value.equalsIgnoreCase(registrationId)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
    }
}

/**
 * AuthProvider Enum과 데이터베이스 간의 변환을 담당하는 JPA 컨버터입니다.
 * <p>
 * AuthProvider에 특화된 변환 로직을 캡슐화하며, 엔티티 필드에서
 * {@code @Convert(converter = AuthProviderConverter.class)}를 통해 호출됩니다.
 */
@Converter(autoApply = false)
class AuthProviderConverter extends StringCodeEnumConverter<AuthProvider> {

    AuthProviderConverter() {
        super(AuthProvider.class);
    }
}
