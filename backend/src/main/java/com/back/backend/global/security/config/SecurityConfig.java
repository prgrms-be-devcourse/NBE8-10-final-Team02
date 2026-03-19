package com.back.backend.global.security.config;

import com.back.backend.global.request.RequestIdFilter;
import com.back.backend.global.security.auth.CookieJwtAuthenticationFilter;
import com.back.backend.global.security.handler.ApiAuthenticationEntryPoint;
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
    private final CookieJwtAuthenticationFilter cookieJwtAuthenticationFilter;

    public SecurityConfig(
            RequestIdFilter requestIdFilter,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            CookieJwtAuthenticationFilter cookieJwtAuthenticationFilter
    ) {
        this.requestIdFilter = requestIdFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.cookieJwtAuthenticationFilter = cookieJwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                        .anyRequest().permitAll()
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
