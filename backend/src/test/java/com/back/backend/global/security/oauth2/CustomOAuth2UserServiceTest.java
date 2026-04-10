package com.back.backend.global.security.oauth2;

import com.back.backend.domain.auth.entity.AuthAccount;
import com.back.backend.domain.auth.entity.AuthProvider;
import com.back.backend.domain.auth.repository.AuthAccountRepository;
import com.back.backend.domain.github.service.GithubConnectionService;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.security.oauth2.app.OAuth2State;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomOAuth2UserServiceTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthAccountRepository authAccountRepository;

    @Mock
    private GithubConnectionService githubConnectionService;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(userRepository, authAccountRepository, githubConnectionService);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void loadUser_googleNewSignupCreatesUserAndPrimaryAuthAccount() {
        stubUserInfo("/userinfo/google", """
                {
                  "sub": "google-123",
                  "name": "New Google User",
                  "picture": "https://img.example/google.png",
                  "email": "google@example.com"
                }
                """);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-123"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
        given(authAccountRepository.save(any(AuthAccount.class))).willAnswer(invocation -> invocation.getArgument(0));

        OAuth2User loaded = service.loadUser(userRequest("google", "/userinfo/google", "sub", "google-token"));

        assertThat(loaded).isInstanceOf(OurOAuth2User.class);
        assertThat(((OurOAuth2User) loaded).getUserId()).isEqualTo(10L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("New Google User");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("google@example.com");

        ArgumentCaptor<AuthAccount> accountCaptor = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(accountCaptor.getValue().isPrimary()).isTrue();
        verify(githubConnectionService, never()).saveConnectionOnly(any(), any(), any(), any());
    }

    @Test
    void loadUser_googleExistingAccountUpdatesProfileAndLastLogin() {
        stubUserInfo("/userinfo/google", """
                {
                  "sub": "google-123",
                  "name": "Updated Name",
                  "picture": "https://img.example/updated.png",
                  "email": "updated@example.com"
                }
                """);
        User existingUser = user(11L, "Old Name", "old@example.com");
        AuthAccount account = AuthAccount.builder()
                .user(existingUser)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-123")
                .providerEmail("old@example.com")
                .primary(true)
                .connectedAt(Instant.now().minusSeconds(600))
                .build();
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-123"))
                .willReturn(Optional.of(account));

        OAuth2User loaded = service.loadUser(userRequest("google", "/userinfo/google", "sub", "google-token"));

        assertThat(((OurOAuth2User) loaded).getUserId()).isEqualTo(11L);
        assertThat(account.getLastLoginAt()).isNotNull();
        assertThat(existingUser.getDisplayName()).isEqualTo("Updated Name");
        assertThat(existingUser.getProfileImageUrl()).isEqualTo("https://img.example/updated.png");
        assertThat(existingUser.getEmail()).isEqualTo("updated@example.com");
        verify(userRepository, never()).save(any());
        verify(authAccountRepository, never()).save(any());
    }

    @Test
    void loadUser_githubLoginWrapsConnectionSaveFailureAndIgnoresInvalidState() {
        stubUserInfo("/userinfo/github", """
                {
                  "id": 12345,
                  "login": "octocat",
                  "name": "Octo Cat",
                  "avatar_url": "https://img.example/octo.png",
                  "email": "octo@example.com"
                }
                """);
        setState("not-valid-state");
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "12345"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });
        given(authAccountRepository.save(any(AuthAccount.class))).willAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("connection conflict"))
                .when(githubConnectionService)
                .saveConnectionOnly(any(User.class), eq(12345L), eq("octocat"), eq("github-token"));

        assertThatThrownBy(() -> service.loadUser(userRequest("github", "/userinfo/github", "id", "github-token")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException error = (OAuth2AuthenticationException) ex;
                    assertThat(error.getError().getErrorCode()).isEqualTo("github_connection_failed");
                });

        verify(userRepository, never()).findById(any());
    }

    @Test
    void loadUser_githubLinkWithExistingAuthAccountReturnsOriginalUserId() {
        stubUserInfo("/userinfo/github-link-existing", """
                {
                  "id": 54321,
                  "login": "linked-cat",
                  "name": "Linked Cat",
                  "avatar_url": "https://img.example/linked.png",
                  "email": "linked@example.com"
                }
                """);
        setState(OAuth2State.ofLink("/settings", 99L).encode());
        User linkUser = user(99L, "Link User", "link@example.com");
        AuthAccount existingGithub = AuthAccount.builder()
                .user(linkUser)
                .provider(AuthProvider.GITHUB)
                .providerUserId("54321")
                .providerEmail("linked@example.com")
                .primary(false)
                .connectedAt(Instant.now().minusSeconds(300))
                .build();
        given(userRepository.findById(99L)).willReturn(Optional.of(linkUser));
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "54321"))
                .willReturn(Optional.of(existingGithub));
        doNothing().when(githubConnectionService).saveConnectionOnly(linkUser, 54321L, "linked-cat", "github-link-token");

        OAuth2User loaded = service.loadUser(userRequest("github", "/userinfo/github-link-existing", "id", "github-link-token"));

        assertThat(((OurOAuth2User) loaded).getUserId()).isEqualTo(99L);
        assertThat(existingGithub.getLastLoginAt()).isNotNull();
        verify(authAccountRepository, never()).save(any());
        verify(githubConnectionService).saveConnectionOnly(linkUser, 54321L, "linked-cat", "github-link-token");
    }

    @Test
    void loadUser_githubLinkCreatesSecondaryAuthAccountWhenMissing() {
        stubUserInfo("/userinfo/github-link-new", """
                {
                  "id": 67890,
                  "login": "new-linked-cat",
                  "name": "New Linked Cat",
                  "avatar_url": "https://img.example/new-linked.png",
                  "email": "new-linked@example.com"
                }
                """);
        setState(OAuth2State.ofLink("/settings", 77L).encode());
        User linkUser = user(77L, "Link User", "link@example.com");
        given(userRepository.findById(77L)).willReturn(Optional.of(linkUser));
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, "67890"))
                .willReturn(Optional.empty());
        given(authAccountRepository.save(any(AuthAccount.class))).willAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(githubConnectionService).saveConnectionOnly(linkUser, 67890L, "new-linked-cat", "github-link-token");

        OAuth2User loaded = service.loadUser(userRequest("github", "/userinfo/github-link-new", "id", "github-link-token"));

        assertThat(((OurOAuth2User) loaded).getUserId()).isEqualTo(77L);
        ArgumentCaptor<AuthAccount> accountCaptor = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(accountCaptor.getValue().isPrimary()).isFalse();
        assertThat(accountCaptor.getValue().getProviderEmail()).isEqualTo("new-linked@example.com");
        verify(githubConnectionService).saveConnectionOnly(linkUser, 67890L, "new-linked-cat", "github-link-token");
    }

    @Test
    void loadUser_kakaoSignupExtractsNestedProviderAttributes() {
        stubUserInfo("/userinfo/kakao", """
                {
                  "id": 555,
                  "properties": {
                    "nickname": "Kakao User",
                    "profile_image": "https://img.example/kakao.png"
                  }
                }
                """);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "555"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 55L);
            return saved;
        });
        given(authAccountRepository.save(any(AuthAccount.class))).willAnswer(invocation -> invocation.getArgument(0));

        OAuth2User loaded = service.loadUser(userRequest("kakao", "/userinfo/kakao", "id", "kakao-token"));

        assertThat(((OurOAuth2User) loaded).getUserId()).isEqualTo(55L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("Kakao User");
        assertThat(userCaptor.getValue().getProfileImageUrl()).isEqualTo("https://img.example/kakao.png");
        assertThat(userCaptor.getValue().getEmail()).isNull();
    }

    private void stubUserInfo(String path, String body) {
        wireMock.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private OAuth2UserRequest userRequest(String registrationId, String userInfoPath, String userNameAttribute, String tokenValue) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://provider.example.com/oauth2/authorize")
                .tokenUri("https://provider.example.com/oauth2/token")
                .userInfoUri(wireMock.baseUrl() + userInfoPath)
                .userNameAttributeName(userNameAttribute)
                .clientName(registrationId)
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(300)
        );
        return new OAuth2UserRequest(registration, token);
    }

    private void setState(String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", state);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private User user(Long id, String name, String email) {
        User user = User.builder()
                .displayName(name)
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
