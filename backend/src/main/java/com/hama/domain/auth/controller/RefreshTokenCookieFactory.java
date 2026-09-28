package com.hama.domain.auth.controller;

import com.hama.global.auth.CookieProperties;
import com.hama.global.auth.JwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키를 만듭니다. 발급과 삭제가 <b>같은 속성</b>을 쓰도록 한 곳에 모았습니다.
 * 브라우저는 이름·path·domain 이 모두 같아야 같은 쿠키로 보고 지웁니다. path 가 하나라도 다르면
 * 로그아웃해도 원래 쿠키가 그대로 남습니다.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenCookieFactory {

    static final String COOKIE_NAME = "refreshToken";

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    ResponseCookie create(String refreshToken) {
        return base(refreshToken)
                // 쿠키 수명을 토큰 수명과 맞춥니다. 쿠키가 더 길면 만료된 토큰을 계속 보내고, 더 짧으면 멀쩡한 토큰을 잃습니다.
                .maxAge(Duration.ofMillis(jwtProperties.refreshTokenValidity()))
                .build();
    }

    ResponseCookie delete() {
        return base("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                // JS 에서 document.cookie 로 읽을 수 없게 합니다. XSS 가 나도 리프레시 토큰은 못 가져갑니다.
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path());
    }
}
