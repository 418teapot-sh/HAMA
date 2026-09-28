package com.hama.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API + 토큰 인증이라 세션·폼 기반 CSRF 보호가 필요 없습니다.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CorsConfig 의 corsConfigurationSource 빈을 그대로 씁니다.
                .cors(Customizer.withDefaults())
                // 인증은 별도 이슈에서 붙입니다. 그때까지 팀원들이 API 를 개발·테스트할 수 있도록
                // 모든 요청을 열어둡니다. ⚠️ 이 상태로 배포하면 안 됩니다.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
