package com.back.backend.global.security.oauth2;

import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.security.auth.entity.AuthAccount;
import com.back.backend.global.security.auth.entity.AuthProvider;
import com.back.backend.global.security.auth.repository.AuthAccountRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 소셜 공급자(Google, GitHub 등)로부터 받은 사용자 정보를 처리하여
 * 우리 시스템의 회원으로 로딩하는 서비스 클래스입니다.
 * <p>
 * 신규 사용자는 자동으로 회원가입을 진행하고, 기존 사용자는 프로필 정보를 최신으로 동기화합니다. (UPSERT)
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;

    public CustomOAuth2UserService(UserRepository userRepository, AuthAccountRepository authAccountRepository) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
    }

    /**
     * OAuth2 인증 성공 후 공급자로부터 받은 액세스 토큰을 사용하여 유저 정보를 가져옵니다.
     * * @param userRequest 공급자 정보와 액세스 토큰이 담긴 요청 객체
     * @return 우리 시스템에서 사용할 Custom OAuth2User 객체
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider authProvider = AuthProvider.fromRegistrationId(registrationId);
        UserInfo info = extractUserInfo(authProvider, oAuth2User);
        Instant now = Instant.now();

        Optional<AuthAccount> existing = authAccountRepository
                .findByProviderAndProviderUserId(authProvider, info.providerUserId());

        User user;
        if (existing.isPresent()) {
            // [기존 유저] 로그인 시간을 갱신하고, 변경된 프로필(이름, 사진 등)을 동기화합니다.
            AuthAccount account = existing.get();
            account.updateLastLoginAt(now);
            user = account.getUser();
            user.updateProfile(info.displayName(), info.profileImageUrl(), info.email());
        } else {
            // [신규 유저] User 엔티티와 소셜 연동 정보를 담은 AuthAccount 엔티티를 새로 생성(회원가입)합니다.
            user = userRepository.save(User.builder()
                    .displayName(info.displayName())
                    .profileImageUrl(info.profileImageUrl())
                    .email(info.email())
                    .status(UserStatus.ACTIVE)
                    .build());
            authAccountRepository.save(AuthAccount.builder()
                    .user(user)
                    .provider(authProvider)
                    .providerUserId(info.providerUserId())
                    .providerEmail(info.email())
                    .primary(true)
                    .connectedAt(now)
                    .lastLoginAt(now)
                    .build());
        }

        // 시큐리티 컨텍스트에 저장될 유저 객체를 반환합니다. (우리 도메인의 User ID 포함)
        return new OurOAuth2User(user.getId(), oAuth2User);
    }

    /**
     * 소셜 공급자별로 다른 유저 정보 속성(Attributes)을 우리 서비스 표준 구조로 변환합니다.
     * * @param provider 소셜 서비스 종류 (KAKAO, GOOGLE, GITHUB)
     * @param oAuth2User 소셜에서 넘어온 원시 유저 정보
     * @return 공통 데이터 전송 객체 UserInfo
     */
    @SuppressWarnings("unchecked")
    private UserInfo extractUserInfo(AuthProvider provider, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();

        return switch (provider) {
            case KAKAO -> {
                Map<String, Object> props = (Map<String, Object>) attrs.get("properties");
                yield new UserInfo(
                        oAuth2User.getName(),
                        (String) props.get("nickname"),
                        (String) props.get("profile_image"),
                        null
                );
            }
            case GOOGLE -> new UserInfo(
                    oAuth2User.getName(),
                    (String) attrs.get("name"),
                    (String) attrs.get("picture"),
                    (String) attrs.get("email")
            );
            case GITHUB -> {
                String name = (String) attrs.get("name");
                String login = (String) attrs.get("login");
                yield new UserInfo(
                        String.valueOf(attrs.get("id")),
                        (name != null && !name.isBlank()) ? name : login,
                        (String) attrs.get("avatar_url"),
                        (String) attrs.get("email")
                );
            }
        };
    }

    /**
     * 내부적으로 사용하는 임시 유저 정보 DTO
     */
    private record UserInfo(String providerUserId, String displayName, String profileImageUrl, String email) {}
}
