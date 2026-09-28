package com.hama.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 응답 body 에는 액세스 토큰만 싣습니다. 프론트는 이 값을 메모리에만 보관합니다.
 *
 * <p>리프레시 토큰은 여기 넣지 않고 HttpOnly 쿠키로만 내려갑니다. body 로 주면 프론트가
 * localStorage 에 저장하게 되고, XSS 한 번에 14일짜리 토큰까지 털려서 액세스 토큰을
 * 30분으로 짧게 둔 의미가 없어집니다.
 */
@Schema(description = "토큰 발급 결과. 리프레시 토큰은 body 가 아니라 Set-Cookie(refreshToken, HttpOnly)로 내려갑니다.")
public record TokenResponse(
        @Schema(description = "액세스 토큰 (30분). 메모리에만 보관하고 `Authorization: Bearer <토큰>` 으로 보냅니다.",
                example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidHlwZSI6ImFjY2VzcyJ9.signature")
        String accessToken
) {
}
