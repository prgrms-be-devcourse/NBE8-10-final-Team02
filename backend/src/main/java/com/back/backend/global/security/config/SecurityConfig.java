package com.back.backend.global.security.config;

import com.back.backend.global.request.RequestIdFilter;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.auth.CookieJwtAuthenticationFilter;
import com.back.backend.global.security.handler.ApiAuthenticationEntryPoint;
import com.back.backend.global.security.jwt.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
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

    @Value("${security.cookie.secure:false}")
    private boolean cookieSecure;

    public SecurityConfig(
            RequestIdFilter requestIdFilter,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            JwtTokenService jwtTokenService,
            ApiKeyService apiKeyService
    ) {
        this.requestIdFilter = requestIdFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
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
            cookieSecure
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
                        .requestMatchers("/actuator/**").permitAll() // 모니터링 허용
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/auth/oauth2/**", "/api/v1/auth/oauth2/**").permitAll()
                        .requestMatchers("/users/**", "/github/**", "/documents/**", "/applications/**", "/interview/**", "/auth/logout").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll() //TODO: 개발완료후 후 수정필요
                )
                // 보통 로그 추적(Logging)을 위해 전체 시스템의 '입구'에서 번표를 나눠주는 작업
                .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class)
                // JWT사용
                .addFilterBefore(cookieJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 운영/개발 환경에 따라 허용 도메인을 다르게 가져가도록 설정
        configuration.setAllowedOrigins(List.of("https://www.blog.jsh505.site", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
