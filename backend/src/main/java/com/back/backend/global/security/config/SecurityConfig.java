package com.back.backend.global.security.config;

import com.back.backend.global.request.RequestIdFilter;
import com.back.backend.global.security.CookieManager;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.auth.CookieJwtAuthenticationFilter;
import com.back.backend.global.security.handler.ApiAuthenticationEntryPoint;
import com.back.backend.global.security.jwt.JwtTokenService;
import com.back.backend.global.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.back.backend.global.security.oauth2.CustomOAuth2AuthorizationRequestResolver;
import com.back.backend.global.security.oauth2.CustomOAuth2LoginSuccessHandler;
import com.back.backend.global.security.oauth2.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RequestIdFilter requestIdFilter;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final JwtTokenService jwtTokenService;
    private final ApiKeyService apiKeyService;
    private final CookieManager cookieManager;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOAuth2LoginSuccessHandler customOAuth2LoginSuccessHandler;
    private final CustomOAuth2AuthorizationRequestResolver customOAuth2AuthorizationRequestResolver;
    private final CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository;

    public SecurityConfig(
            RequestIdFilter requestIdFilter,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            JwtTokenService jwtTokenService,
            ApiKeyService apiKeyService,
            CookieManager cookieManager,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOAuth2LoginSuccessHandler customOAuth2LoginSuccessHandler,
            CustomOAuth2AuthorizationRequestResolver customOAuth2AuthorizationRequestResolver,
            CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository
    ) {
        this.requestIdFilter = requestIdFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
        this.cookieManager = cookieManager;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOAuth2LoginSuccessHandler = customOAuth2LoginSuccessHandler;
        this.customOAuth2AuthorizationRequestResolver = customOAuth2AuthorizationRequestResolver;
        this.cookieOAuth2AuthorizationRequestRepository = cookieOAuth2AuthorizationRequestRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 필터를 여기서 직접 생성(new)합니다.
        // CookieFilter의 @Component를 뗐기 때문에 오직 여기서만 생성되어 시큐리티 체인에만 들어갑니다.
        // 필터의 생명주기 주도권이 '스프링 부트'에서 **'스프링 시큐리티'**로 완전히 넘어옵니다.
        RequestIdFilter requestIdFilter = new RequestIdFilter();

        CookieJwtAuthenticationFilter cookieJwtAuthenticationFilter = new CookieJwtAuthenticationFilter(
            jwtTokenService,
            apiKeyService,
            apiAuthenticationEntryPoint,
            cookieManager
        );

        http
                .csrf(csrf -> csrf.disable()) // API 서버이므로 대개 비활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // /oauth2/authorization/** (모든 소셜 로그인 시작점)
                        // /login/oauth2/code/** (모든 소셜 로그인 도착점)
                        // ex) /oauth2/authorization/google, /login/oauth2/code/github
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        .requestMatchers("/api/v1/auth/oauth2/github/link-url").authenticated() // 연동 URL 생성은 로그인 필요 (permitAll 와일드카드보다 먼저 선언)
                        .requestMatchers("/auth/oauth2/**", "/api/v1/auth/oauth2/**").permitAll()
                        .requestMatchers("/api/v1/auth/logout").permitAll() // 만료된 토큰으로도 로그아웃 가능
                        .requestMatchers("/api/v1/ai/status").permitAll() // AI 가용성 상태 조회는 인증 불필요
                        .requestMatchers("/api/v1/knowledge/sync")
                            .access(new WebExpressionAuthorizationManager(
                                "hasIpAddress('127.0.0.1') or hasIpAddress('::1')"))
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(customOAuth2AuthorizationRequestResolver)
                                // 세션 대신 쿠키에 OAuth2 인증 요청 객체를 저장 → STATELESS 유지
                                .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository)
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(customOAuth2LoginSuccessHandler)
                )
                .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(cookieJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 운영/개발 환경에 따라 허용 도메인을 다르게 가져가도록 설정
        configuration.setAllowedOrigins(List.of("https://www.blog.jsh505.site", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
