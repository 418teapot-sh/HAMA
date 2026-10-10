package com.hama.global.config;

import com.hama.global.auth.JwtAccessDeniedHandler;
import com.hama.global.auth.JwtAuthenticationEntryPoint;
import com.hama.global.auth.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 인증 없이 접근할 수 있는 경로. 나머지는 전부 로그인이 필요합니다.
     *
     * <p>⚠️ actuator 는 {@code /actuator/**} 로 묶지 마세요. 나중에 메트릭을 붙이려고
     * {@code exposure.include} 를 늘리는 순간 {@code /actuator/env} 에서 JWT_SECRET · DB 비밀번호 ·
     * 라이너 키가 무인증으로 보입니다. 필요한 엔드포인트가 생기면 그때 하나씩 추가하세요.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/info",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API + 토큰 인증이라 세션·폼 기반 CSRF 보호가 필요 없습니다.
                // (리프레시 쿠키는 SameSite 와 좁은 path(/api/v1/auth) 로 보호합니다)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CorsConfig 의 corsConfigurationSource 빈을 그대로 씁니다.
                .cors(Customizer.withDefaults())
                // 켜두면 인증 실패 시 로그인 HTML 페이지나 브라우저 Basic 인증 팝업이 나갑니다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())

                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)   // 401
                        .accessDeniedHandler(jwtAccessDeniedHandler))            // 403

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JwtAuthenticationFilter 의 서블릿 컨테이너 자동 등록을 끕니다.
     *
     * <p>Boot 는 {@code Filter} 빈을 보면 서블릿 필터로도 자동 등록합니다. 그러면 같은 필터가
     * 시큐리티 체인 바깥에도 하나 더 걸린 상태가 되어, 나중에 {@code securityMatcher} 나 두 번째
     * {@code SecurityFilterChain} 을 추가했을 때 보호 범위가 헷갈립니다. 등록 위치를 시큐리티 체인 하나로 못박습니다.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /** 회원가입·로그인에서 비밀번호를 해시하고 검증합니다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
