package com.hama.domain.auth.service;

/**
 * 서비스가 컨트롤러에 넘기는 발급 결과입니다. 응답 DTO 가 아닙니다.
 * 컨트롤러가 accessToken 은 body 로, refreshToken 은 HttpOnly 쿠키로 나눠 내려보냅니다.
 */
public record AuthTokens(String accessToken, String refreshToken) {
}
