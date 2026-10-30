package com.hama.domain.auth.controller;

import com.hama.domain.auth.dto.EmailInquiryRequest;
import com.hama.domain.auth.dto.LoginRequest;
import com.hama.domain.auth.dto.PasswordResetConfirmRequest;
import com.hama.domain.auth.dto.PasswordResetRequest;
import com.hama.domain.auth.dto.SignupRequest;
import com.hama.domain.auth.dto.TokenResponse;
import com.hama.domain.auth.service.AccountRecoveryService;
import com.hama.domain.auth.service.AuthService;
import com.hama.domain.auth.service.AuthTokens;
import com.hama.global.auth.RefreshTokenCookieFactory;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
        회원가입 · 로그인 · 토큰 재발급 · 로그아웃 · 비밀번호 재설정 · 이메일 찾기 문의.
        액세스 토큰은 응답 body 로, 리프레시 토큰은 HttpOnly 쿠키로 내려갑니다.
        프론트는 요청에 `credentials: 'include'`(axios 는 `withCredentials: true`)를 켜야 쿠키가 오갑니다.
        """)
@SecurityRequirements   // 전부 공개 API 라 Swagger 의 기본 Bearer 요구를 풉니다.
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountRecoveryService accountRecoveryService;
    private final RefreshTokenCookieFactory cookieFactory;

    @Operation(summary = "회원가입",
            description = """
                    가입과 동시에 로그인 처리됩니다. body 에 accessToken, Set-Cookie 에 refreshToken 이 옵니다.
                    이메일이 이미 가입되어 있으면 409(EMAIL_ALREADY_EXISTS)입니다.
                    같은 IP 에서 15분에 30회까지 가입할 수 있고, 넘으면 429(TOO_MANY_REQUESTS)입니다.
                    """)
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request,
                                                             HttpServletRequest servletRequest) {
        return withRefreshCookie(authService.signup(request, servletRequest.getRemoteAddr()));
    }

    @Operation(summary = "로그인",
            description = """
                    body 에 accessToken, Set-Cookie 에 refreshToken 이 옵니다.
                    이메일이 없거나 비밀번호가 틀려도 구분하지 않고 401(INVALID_CREDENTIALS)입니다.
                    같은 IP 에서 이메일 하나당 15분에 5회, 이메일과 상관없이 50회까지 시도할 수 있고, 넘으면 429(TOO_MANY_PASSWORD_ATTEMPTS)입니다.
                    """)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest servletRequest) {
        // forward-headers-strategy: native 가 X-Forwarded-For 를 오른쪽부터 읽어 프록시가 붙인 진짜 접속 IP 를 쓰므로 위조할 수 없습니다.
        return withRefreshCookie(authService.login(request, servletRequest.getRemoteAddr()));
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

    @Operation(summary = "비밀번호 재설정 메일 요청",
            description = """
                    가입된 이메일이면 재설정 링크(`{프론트}/reset-password?token=...`, 30분, 한 번만 사용)를 메일로 보냅니다.
                    가입 여부를 숨기려고 없는 이메일이어도 똑같이 200 입니다. 다시 요청하면 새 링크가 가고 옛 링크는 무효가 됩니다.
                    이메일·IP 마다 15분에 5번까지이고, 넘으면 429(TOO_MANY_REQUESTS)입니다.
                    """)
    @PostMapping("/password-reset")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                  HttpServletRequest servletRequest) {
        accountRecoveryService.requestPasswordReset(request.email(), servletRequest.getRemoteAddr());
        return ApiResponse.noContent();
    }

    @Operation(summary = "새 비밀번호 설정",
            description = """
                    재설정 링크의 token 과 새 비밀번호로 비밀번호를 바꿉니다. 바꾸면 모든 기기의 리프레시 토큰이 지워지니 새 비밀번호로 로그인하게 하세요.
                    이미 발급된 액세스 토큰은 만료(30분)까지 그대로 통과합니다.
                    성공하면 그 이메일의 로그인 시도 제한(429)도 풀립니다.
                    링크가 만료(30분)됐거나, 이미 썼거나, 메일을 다시 받아 바뀌었으면 400(INVALID_PASSWORD_RESET_TOKEN)입니다.
                    새 비밀번호 규칙은 회원가입과 같습니다(어기면 400 VALIDATION_FAILED).
                    """)
    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        accountRecoveryService.confirmPasswordReset(request);
        return ApiResponse.noContent();
    }

    @Operation(summary = "이메일 찾기 문의",
            description = """
                    이름과 답변받을 이메일을 남기면 운영자가 가입 정보를 확인해 메일로 안내합니다(자동으로 찾아주지 않음).
                    IP 마다 15분에 5번까지이고, 넘으면 429(TOO_MANY_REQUESTS)입니다.
                    """)
    @PostMapping("/email-inquiry")
    public ApiResponse<Void> submitEmailInquiry(@Valid @RequestBody EmailInquiryRequest request,
                                                HttpServletRequest servletRequest) {
        accountRecoveryService.submitEmailInquiry(request, servletRequest.getRemoteAddr());
        return ApiResponse.noContent();
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withRefreshCookie(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.refreshToken()).toString())
                .body(ApiResponse.success(new TokenResponse(tokens.accessToken())));
    }
}
