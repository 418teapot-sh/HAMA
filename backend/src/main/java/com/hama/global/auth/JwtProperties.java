package com.hama.global.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 jwt.* 설정값.
 *
 * @param secret               HS256 서명 키. dev 에서는 반드시 환경변수 JWT_SECRET 로 주입합니다
 * @param accessTokenValidity  액세스 토큰 유효시간 (ms)
 * @param refreshTokenValidity 리프레시 토큰 유효시간 (ms). 리프레시 쿠키의 Max-Age 도 이 값을 씁니다
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidity,
        long refreshTokenValidity
) {
}
