package com.hama.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 프론트엔드 연동을 위한 CORS 설정.
 * 허용 주소는 application.yml 의 {@code cors.allowed-origins} 에서 관리합니다.
 *
 * <p>Refresh Token 을 HttpOnly 쿠키로 주고받을 예정이라 {@code allowCredentials(true)} 가 필요합니다.
 * 그런데 credentials 를 허용하면 {@code setAllowedOrigins} 에 와일드카드({@code *})를 쓸 수 없습니다.
 * Vercel 은 브랜치·커밋마다 프리뷰 주소가 새로 생겨서 패턴이 필요하므로
 * {@code setAllowedOriginPatterns} 로 등록합니다. (예: {@code https://*-hama.vercel.app})
 *
 * <p>⚠️ 패턴에 {@code *} 하나만 넣지 마세요. credentials 허용과 합쳐지면 아무 사이트나
 * 사용자 쿠키를 실어 API 를 호출할 수 있게 됩니다.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // TraceIdFilter 가 실어 보내는 X-Trace-Id 를 프론트가 읽을 수 있어야 합니다. CORS 는
        // 노출 목록에 없는 헤더를 크로스 오리진 JS 에서 못 읽게 막습니다.
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
