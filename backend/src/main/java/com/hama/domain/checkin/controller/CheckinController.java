package com.hama.domain.checkin.controller;

import com.hama.domain.checkin.dto.CheckinResponses;
import com.hama.domain.checkin.dto.CreateCheckinRequest;
import com.hama.domain.checkin.dto.UpdateCheckinAchievementRequest;
import com.hama.domain.checkin.service.CheckinService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import com.hama.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표 체크인", description = "시작·중간·종료 측정 기록. END는 목표 상태를 변경하지 않습니다.")
@RestController
@RequestMapping("/api/v1/todos/state/checkins")
@RequiredArgsConstructor
public class CheckinController {
    private final CheckinService service;

    @Operation(summary = "목표 체크인 입력", description = "진행 중·지난 목표. START/END 각 1회(중복 409), MID 여러 회. 순서 자유, 미래 시각 400. 타인 403, 없음·삭제 404.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CheckinResponses.Created> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody CreateCheckinRequest request) {
        return ApiResponse.success(service.create(user.userId(), request));
    }

    @Operation(summary = "END 체크인 달성 여부 수정", description = "본인 진행 중·지난 목표의 END만 수정합니다. achieved는 true/false 필수(생략·null·다른 타입 400). "
            + "START/MID·계획 중 목표 409, 타인 403, 없음·삭제 목표 404. 측정값·메모·기록 시각·목표 상태는 변경하지 않습니다. "
            + "성공 200으로 수정한 체크인을 반환하며 같은 값 재요청도 허용합니다.")
    @PatchMapping("/{checkinId}")
    public ApiResponse<CheckinResponses.Item> updateAchievement(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @PathVariable Long checkinId,
            @Valid @RequestBody UpdateCheckinAchievementRequest request) {
        return ApiResponse.success(service.updateAchievement(user.userId(), checkinId, request));
    }

    @Operation(summary = "목표 체크인 이력", description = "단위와 공통 페이징을 반환합니다. checkedAt 내림차순, 같으면 ID 내림차순. 삭제된 목표는 404.")
    @GetMapping
    public ApiResponse<CheckinResponses.History> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user,
            @RequestParam Long goalId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ApiResponse.success(service.list(user.userId(), goalId, PageResponse.pageRequest(page, size)));
    }
}
