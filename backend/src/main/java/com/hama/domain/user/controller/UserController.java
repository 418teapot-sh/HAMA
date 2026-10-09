package com.hama.domain.user.controller;

import com.hama.domain.user.dto.UserResponse;
import com.hama.domain.user.dto.WithdrawRequest;
import com.hama.domain.user.service.UserService;
import com.hama.global.auth.AuthUser;
import com.hama.global.auth.RefreshTokenCookieFactory;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "로그인한 사용자 정보. 모든 API 에 액세스 토큰(Bearer)이 필요합니다.")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RefreshTokenCookieFactory cookieFactory;

    @Operation(summary = "내 정보 조회",
            description = """
                    액세스 토큰의 사용자 정보를 돌려줍니다.
                    토큰이 없거나 만료되면 401(UNAUTHORIZED)이고, 그때는 /api/auth/refresh 로 재발급받으세요.
                    """)
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(userService.getMe(authUser.userId()));
    }

    @Operation(summary = "회원탈퇴",
            description = """
                    비밀번호를 확인한 뒤 계정과 투두·일정·목표 등 모든 데이터를 지우고, 모든 기기의 로그인을 끊습니다.
                    결제 기록은 법정 보관 기간 동안 남습니다. 되돌릴 수 없습니다.
                    비밀번호가 틀리면 400(PASSWORD_MISMATCH)이고, 15분에 5회를 넘게 시도하면 429(TOO_MANY_PASSWORD_ATTEMPTS)입니다.
                    성공하면 리프레시 토큰 쿠키가 지워집니다. 프론트는 메모리의 accessToken 도 버리세요.
                    """)
    @PostMapping("/me/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody WithdrawRequest request) {
        userService.withdraw(authUser.userId(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.delete().toString())
                .body(ApiResponse.noContent());
    }
}
