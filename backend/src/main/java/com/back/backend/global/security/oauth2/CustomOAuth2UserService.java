package com.back.backend.global.security.oauth2;

import com.back.backend.domain.github.service.GithubConnectionService;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.domain.auth.entity.AuthAccount;
import com.back.backend.domain.auth.entity.AuthProvider;
import com.back.backend.domain.auth.repository.AuthAccountRepository;
import com.back.backend.global.security.oauth2.app.OAuth2State;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    private final GithubConnectionService githubConnectionService;

    public CustomOAuth2UserService(
            UserRepository userRepository,
            AuthAccountRepository authAccountRepository,
            GithubConnectionService githubConnectionService
    ) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
        this.githubConnectionService = githubConnectionService;
    }

    /**
     * OAuth2 인증 성공 후 공급자로부터 받은 액세스 토큰을 사용하여 유저 정보를 가져옵니다.
     *
     * @param userRequest 공급자 정보와 액세스 토큰이 담긴 요청 객체
     * @return 우리 시스템에서 사용할 Custom OAuth2User 객체
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider authProvider = AuthProvider.fromRegistrationId(registrationId);

        // state에서 linkUserId 확인: 값이 있으면 계정 연동 흐름, 없으면 일반 로그인 흐름
        Long linkUserId = extractLinkUserIdFromState();
        if (linkUserId != null && authProvider == AuthProvider.GITHUB) {
            return handleGithubLink(oAuth2User, userRequest, linkUserId);
        }

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

        // GitHub OAuth 로그인 시 token을 github_connections에 저장한다.
        if (authProvider == AuthProvider.GITHUB) {
            String githubToken = userRequest.getAccessToken().getTokenValue();
            Long githubUserId = ((Number) oAuth2User.getAttributes().get("id")).longValue();
            String githubLogin = (String) oAuth2User.getAttributes().get("login");
            try {
                githubConnectionService.saveConnectionOnly(user, githubUserId, githubLogin, githubToken);
            } catch (Exception e) {
                // 일반 로그인 과정에서의 GitHub 연결 실패(예: 계정 충돌) 처리
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("github_connection_failed", e.getMessage(), null), e.getMessage(), e);
            }
        }

        // 시큐리티 컨텍스트에 저장될 유저 객체를 반환합니다. (우리 도메인의 User ID 포함)
        return new OurOAuth2User(user.getId(), oAuth2User);
    }

    /**
     * OAuth2 콜백 요청의 state 파라미터에서 linkUserId를 추출한다.
     * 연동 흐름이 아니면 null을 반환한다.
     */
    private Long extractLinkUserIdFromState() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            String stateParam = attrs.getRequest().getParameter("state");
            if (stateParam == null || stateParam.isBlank()) return null;
            return OAuth2State.decode(stateParam).getLinkUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 이미 로그인된 사용자(linkUserId)에게 GitHub 계정을 추가 연동한다.
     * AuthAccount를 추가하고 GithubConnection을 저장한 후, 원래 사용자 ID로 JWT를 발급한다.
     */
    private OAuth2User handleGithubLink(OAuth2User oAuth2User, OAuth2UserRequest userRequest, Long linkUserId) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        String githubLogin = (String) attrs.get("login");
        String githubProviderUserId = String.valueOf(attrs.get("id"));
        Long githubUserId = ((Number) attrs.get("id")).longValue();
        String email = (String) attrs.get("email");
        Instant now = Instant.now();

        User user = userRepository.findById(linkUserId)
                .orElseThrow(() -> new RuntimeException("연동 대상 사용자를 찾을 수 없습니다: " + linkUserId));

        // GitHub AuthAccount 추가 (없으면 생성, 있으면 마지막 로그인 시간만 갱신)
        Optional<AuthAccount> existingAccount = authAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.GITHUB, githubProviderUserId);
        if (existingAccount.isPresent()) {
            existingAccount.get().updateLastLoginAt(now);
        } else {
            authAccountRepository.save(AuthAccount.builder()
                    .user(user)
                    .provider(AuthProvider.GITHUB)
                    .providerUserId(githubProviderUserId)
                    .providerEmail(email)
                    .primary(false)
                    .connectedAt(now)
                    .lastLoginAt(now)
                    .build());
        }

        // GitHub Connection 저장 (token 포함)
        String githubToken = userRequest.getAccessToken().getTokenValue();
        try {
            githubConnectionService.saveConnectionOnly(user, githubUserId, githubLogin, githubToken);
        } catch (Exception e) {
            // ServiceException 등 RuntimeException을 OAuth2AuthenticationException으로 감싸
            // → CustomOAuth2LoginFailureHandler가 잡아 프론트엔드로 에러와 함께 리다이렉트
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("github_link_failed", e.getMessage(), null), e.getMessage(), e);
        }

        // 원래 사용자 ID로 JWT 발급
        return new OurOAuth2User(linkUserId, oAuth2User);
    }

    /**
     * 소셜 공급자별로 다른 유저 정보 속성(Attributes)을 우리 서비스 표준 구조로 변환합니다.
     *
     * @param provider 소셜 서비스 종류 (KAKAO, GOOGLE, GITHUB)
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
