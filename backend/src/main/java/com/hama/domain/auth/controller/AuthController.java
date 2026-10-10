package com.hama.domain.auth.controller;

import com.hama.domain.auth.dto.LoginRequest;
import com.hama.domain.auth.dto.SignupRequest;
import com.hama.domain.auth.dto.TokenResponse;
import com.hama.domain.auth.service.AuthService;
import com.hama.domain.auth.service.AuthTokens;
import com.hama.global.auth.RefreshTokenCookieFactory;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = """
        회원가입 · 로그인 · 토큰 재발급 · 로그아웃.
        액세스 토큰은 응답 body 로, 리프레시 토큰은 HttpOnly 쿠키로 내려갑니다.
        프론트는 요청에 `credentials: 'include'`(axios 는 `withCredentials: true`)를 켜야 쿠키가 오갑니다.
        """)
@SecurityRequirements   // 전부 공개 API 라 Swagger 의 기본 Bearer 요구를 풉니다.
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    @Operation(summary = "회원가입",
            description = """
                    가입과 동시에 로그인 처리됩니다. body 에 accessToken, Set-Cookie 에 refreshToken 이 옵니다.
                    이메일이 이미 가입되어 있으면 409(EMAIL_ALREADY_EXISTS)입니다.
                    """)
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return withRefreshCookie(authService.signup(request));
    }

    @Operation(summary = "로그인",
            description = """
                    body 에 accessToken, Set-Cookie 에 refreshToken 이 옵니다.
                    이메일이 없거나 비밀번호가 틀려도 구분하지 않고 401(INVALID_CREDENTIALS)입니다.
                    이메일 하나당 15분에 5회까지 시도할 수 있고, 넘으면 429(TOO_MANY_PASSWORD_ATTEMPTS)입니다.
                    """)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @Operation(summary = "토큰 재발급",
            description = """
                    요청 body 없이 refreshToken 쿠키만으로 호출합니다.
                    새 accessToken 과 새 refreshToken 쿠키가 옵니다(리프레시 토큰도 매번 교체).
                    쿠키가 없거나 무효하면 401(INVALID_REFRESH_TOKEN)이고, 그때는 다시 로그인해야 합니다.
                    """)
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            // 브라우저가 알아서 붙이는 HttpOnly 쿠키라 Swagger 입력칸에서 숨깁니다.
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        return withRefreshCookie(authService.reissue(refreshToken));
    }

    @Operation(summary = "로그아웃",
            description = """
                    서버에 저장된 리프레시 토큰을 지우고 쿠키도 삭제합니다.
                    토큰이 없거나 이미 무효해도 성공입니다(멱등). 프론트는 메모리의 accessToken 도 버리세요.
                    """)
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.delete().toString())
                .body(ApiResponse.noContent());
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withRefreshCookie(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.refreshToken()).toString())
                .body(ApiResponse.success(new TokenResponse(tokens.accessToken())));
    }
}
