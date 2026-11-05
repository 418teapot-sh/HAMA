package com.hama.domain.calendar.google;

import com.hama.domain.calendar.google.importer.GoogleCalendarImportResult;
import com.hama.domain.calendar.google.importer.GoogleCalendarImportService;
import com.hama.domain.calendar.google.oauth.GoogleCalendarOAuthService;
import com.hama.domain.shared.json.StrictDeserializers;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.annotation.JsonDeserialize;

@RestController
@RequestMapping("/api/v1/calendar/import/google")
public class GoogleCalendarImportController {
    private final GoogleCalendarOAuthService oauth;
    private final GoogleCalendarImportService importer;

    public GoogleCalendarImportController(GoogleCalendarOAuthService oauth, GoogleCalendarImportService importer) {
        this.oauth = oauth;
        this.importer = importer;
    }

    @PostMapping("/{sessionId}/import")
    @Operation(summary = "Google 캘린더 일회성 가져오기", description = "읽을 수 있는 캘린더의 선택 기간(양끝 포함 최대 366일)에서 "
            + "반복 발생 건을 포함해 최대 1,000개를 PERSONAL 일정으로 복사합니다. 성공 후 단기 세션은 삭제됩니다. "
            + "중복은 건너뛰고 지원하지 않는 항목은 사유별로 집계합니다. 원본과 자동 동기화하지 않습니다.")
    public ApiResponse<GoogleCalendarImportResult> importCalendar(@Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @PathVariable String sessionId, @Valid @RequestBody Request request) {
        long userId = user.userId();
        return ApiResponse.success(oauth.withSessionToken(userId, sessionId, token -> {
            var calendar = oauth.requireCalendar(token, request.calendarId());
            return importer.importEvents(userId, token, calendar.id(), request.from(), request.to(),
                    () -> oauth.lockForImport(userId, sessionId),
                    () -> oauth.deleteAfterSuccessfulImport(userId, sessionId));
        }));
    }

    public record Request(@NotBlank @Size(max = 1024) String calendarId,
            @NotNull @JsonDeserialize(using = StrictDeserializers.Date.class) LocalDate from,
            @NotNull @JsonDeserialize(using = StrictDeserializers.Date.class) LocalDate to) { }
}
