package com.hama.domain.user.controller;

import com.hama.domain.user.dto.UserResponse;
import com.hama.domain.user.service.UserService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "로그인한 사용자 정보. 모든 API 에 액세스 토큰(Bearer)이 필요합니다.")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회",
            description = """
                    액세스 토큰의 사용자 정보를 돌려줍니다.
                    토큰이 없거나 만료되면 401(UNAUTHORIZED)이고, 그때는 /api/auth/refresh 로 재발급받으세요.
                    """)
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(userService.getMe(authUser.userId()));
    }
}
