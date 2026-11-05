package com.hama.domain.calendar.google.oauth;

import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar/import/google")
@Tag(name = "Google 캘린더 가져오기")
public class GoogleCalendarOAuthController {
    private final GoogleCalendarOAuthService service;
    public GoogleCalendarOAuthController(GoogleCalendarOAuthService service) { this.service = service; }

    @PostMapping("/authorization")
    @Operation(summary = "일회성 Google 캘린더 인증 시작", description = "인증된 HAMA 사용자에게 바인딩된 10분 세션과 Google 인증 URL. "
            + "온라인 읽기 전용 권한이며 서버 설정의 고정 redirect URI만 사용합니다. 설정 누락 시 503 GOOGLE_IMPORT_DISABLED.")
    public ApiResponse<GoogleCalendarOAuthService.Authorization> authorize(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(service.begin(user.userId()));
    }

    @PostMapping("/authorization/complete")
    @Operation(summary = "Google 인증 코드 교환", description = "Google에서 프론트 redirect URI로 돌아온 state/code와 발급된 sessionId를 전달합니다. "
            + "최초 한 번만 교환하며 토큰은 응답하지 않습니다. 실패하면 인증을 새로 시작합니다. 만료410, 타인403, 잘못된state/재전송400.")
    public ApiResponse<GoogleCalendarOAuthService.Authorized> complete(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @RequestBody GoogleCalendarOAuthService.CompleteRequest request) {
        return ApiResponse.success(service.complete(user.userId(), request));
    }

    @GetMapping("/{sessionId}/calendars")
    @Operation(summary = "읽을 수 있는 Google 캘린더 목록", description = "Google 인증을 마친 같은 사용자만 조회. 만료410, 인증 미완료409, 타인403.")
    public ApiResponse<List<GoogleCalendarAccount>> calendars(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @PathVariable String sessionId) {
        return ApiResponse.success(service.listCalendars(user.userId(), sessionId));
    }
}
