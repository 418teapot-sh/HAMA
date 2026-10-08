package com.hama.domain.calendar.controller;

import com.hama.domain.calendar.dto.CalendarQuery;
import com.hama.domain.calendar.dto.CalendarResponse;
import com.hama.domain.calendar.service.CalendarService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@Tag(name = "캘린더", description = "일반 TASK·목표 투두·고정/개인 일정 조회 및 내보내기.")
public class CalendarController {

    private final CalendarService service;

    @Operation(summary = "캘린더 통합 조회", description = "KST 날짜 from/to를 포함하여 최대 366일. types 생략 시 전체. "
            + "반복·여러 날 일정은 날짜별로 반환하며 겹침은 계산하지 않습니다. 시간 없는 TASK는 종일입니다. "
            + "날짜별 최종 항목은 최대 100개이며 초과하면 CALENDAR_RESULT_LIMIT_EXCEEDED(400)를 반환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "캘린더 조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "기간/타입 오류 또는 날짜별 최종 항목 100개 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "406", description = "Accept 형식 불일치 (CALENDAR_NOT_ACCEPTABLE)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping
    public ApiResponse<CalendarResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Parameter(required = true, example = "2026-10-01") @RequestParam(required = false) String from,
            @Parameter(required = true, example = "2026-10-31") @RequestParam(required = false) String to,
            @Parameter(description = "쉼표로 구분: FIXED,PERSONAL,AI_GOAL,TASK") @RequestParam(required = false) String types) {
        return ApiResponse.success(service.get(user.userId(), CalendarQuery.parse(from, to, types)));
    }

    @Operation(summary = "캘린더 ICS 내보내기", description = "양끝 포함 최대 366일. 기간에 발생하는 반복 일정은 원본 DTSTART와 RRULE을 보존합니다. "
            + "성공은 ICS 파일, 실패는 공통 JSON입니다. 가져온 캘린더에 선택 범위 밖 반복도 보일 수 있습니다. "
            + "기간에 발생하는 원본 일정·투두는 최대 100개이며 반복 횟수나 날짜별 분할은 별도로 세지 않습니다. "
            + "초과하면 CALENDAR_RESULT_LIMIT_EXCEEDED(400)를 반환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ICS 파일",
            content = @Content(mediaType = "text/calendar", schema = @Schema(type = "string", format = "binary")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "기간/타입 오류 또는 원본 일정·투두 100개 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "406", description = "Accept 형식 불일치 (CALENDAR_NOT_ACCEPTABLE)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Parameter(required = true, example = "2026-10-01") @RequestParam(required = false) String from,
            @Parameter(required = true, example = "2026-10-31") @RequestParam(required = false) String to,
            @Parameter(description = "쉼표로 구분: FIXED,PERSONAL,AI_GOAL,TASK. 생략 시 전체") @RequestParam(required = false) String types) {
        CalendarQuery query = CalendarQuery.parse(from, to, types);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hama-" + query.from() + "-" + query.to() + ".ics\"")
                .body(service.export(user.userId(), query));
    }
}
