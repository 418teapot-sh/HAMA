package com.hama.global.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.cookie.* 설정값. 리프레시 토큰 쿠키에 씁니다.
 *
 * @param path     쿠키가 붙는 경로. {@code /api/v1/auth} 로 좁혀서 재발급·로그아웃 요청에만 실리게 합니다
 * @param secure   HTTPS 에서만 전송. 로컬(http)에서 true 면 브라우저가 쿠키를 저장하지 않습니다
 * @param sameSite {@code Lax} / {@code Strict} / {@code None}. 프론트와 API 가 완전히 다른 도메인이면 None
 */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
        String path,
        boolean secure,
        String sameSite
) {
}
