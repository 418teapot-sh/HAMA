package com.hama.domain.schedule.controller;

import com.hama.domain.schedule.dto.CreateScheduleRequest;
import com.hama.domain.schedule.dto.ScheduleResponses;
import com.hama.domain.schedule.dto.UpdateScheduleRequest;
import com.hama.domain.schedule.service.ScheduleService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "일정", description = "고정·개인 일정 관리. 반복 일정 수정·삭제는 시리즈 전체에 적용됩니다.")
@RestController
@RequestMapping("/api/v1/calendar/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService service;

    @Operation(summary = "고정·개인 일정 생성", description = "KST 일시. 종일 일정은 00:00부터 마지막 날 다음 날 00:00까지. "
            + "RRULE은 DAILY/WEEKLY, INTERVAL, COUNT 또는 UNTIL, WEEKLY BYDAY를 지원합니다. "
            + "종일 UNTIL은 YYYYMMDD, 시간 일정 UNTIL은 UTC YYYYMMDDTHHMMSSZ. BYDAY는 시작 요일을 포함해야 합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ScheduleResponses.Created>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(authUser.userId(), request)));
    }

    @Operation(summary = "일정 상세 조회", description = "타인 일정은 403, 없거나 삭제된 일정은 404.")
    @GetMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponses.Detail> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long scheduleId) {
        return ApiResponse.success(service.get(authUser.userId(), scheduleId));
    }

    @Operation(summary = "일정 부분 수정", description = "생략 필드는 유지합니다. repeatRule/memo는 null로 지울 수 있고 "
            + "필수 필드 null은 400입니다. 병합 결과를 검증하며 반복 일정은 시리즈 전체를 수정합니다.")
    @PatchMapping("/{scheduleId}")
    public ApiResponse<ScheduleResponses.Detail> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long scheduleId, @Valid @RequestBody UpdateScheduleRequest request) {
        return ApiResponse.success(service.update(authUser.userId(), scheduleId, request));
    }

    @Operation(summary = "일정 삭제", description = "시리즈 전체를 소프트 삭제합니다. 성공 시 HTTP 200, 공통 응답 data는 null.")
    @DeleteMapping("/{scheduleId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long scheduleId) {
        service.delete(authUser.userId(), scheduleId);
        return ApiResponse.noContent();
    }
}
