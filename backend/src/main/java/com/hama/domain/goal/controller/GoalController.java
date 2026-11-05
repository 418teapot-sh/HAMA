package com.hama.domain.goal.controller;

import com.hama.domain.goal.dto.CreateGoalRequest;
import com.hama.domain.goal.dto.CurrentStatusResponse;
import com.hama.domain.goal.dto.FixedScheduleResponses;
import com.hama.domain.goal.dto.GoalResponses;
import com.hama.domain.goal.dto.UpdateCurrentStatusRequest;
import com.hama.domain.goal.dto.UpdateGoalRequest;
import com.hama.domain.goal.entity.GoalListFilter;
import com.hama.domain.goal.service.GoalService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import com.hama.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표", description = """
        목표 직접 생성·조회·수정·완료·삭제. 진행 중인 목표의 종료일이 오늘(KST)보다 이전이면 status 는 PAST(지난 목표)입니다.
        진행률(progressRate) = 완료된 AI_GOAL_TASK ÷ 전체 AI_GOAL_TASK × 100, 투두가 없으면 0.0.
        aiPlanApplied 는 goals/ai/plans 로 만든 플랜을 선택해 투두가 생긴 목표인지 여부입니다.
        """)
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @Operation(summary = "목표 리스트 조회",
            description = """
                    status: IN_PROGRESS(진행 중, 마감 가까운 순) | PAST(지난 목표, 최근 종료 순) | COMPLETED(완료, 최근 완료한 순),
                    생략하면 플랜 선택 전(PLANNING)까지 포함한 전체(최근 생성 순). page 는 0부터, size 는 1~100(기본 20).
                    status 값이 잘못되면 400(BINDING_ERROR), page·size 범위를 벗어나면 400(VALIDATION_FAILED).
                    """)
    @GetMapping
    public ApiResponse<PageResponse<GoalResponses.Summary>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) GoalListFilter status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ApiResponse.success(goalService.list(authUser.userId(), status, PageResponse.pageRequest(page, size)));
    }

    @Operation(summary = "목표 직접 생성",
            description = """
                    목표(title)·현재 상태(currentStatus, 직접 입력)·기간·고정 일정을 받아 AI 없이 바로 만듭니다.
                    투두는 아직 없어 PLANNING 으로 시작하며, goals/ai/plans 로 A/B 플랜을 만들고 선택해야 투두가 생기고 IN_PROGRESS 가 됩니다.
                    시작일 > 종료일이거나 종료일이 오늘 이전이면 400(GOAL_INVALID_PERIOD), 필드 검증 실패는 400(VALIDATION_FAILED).
                    기간은 시작일 포함 최대 365일이고, 넘으면 400(GOAL_PERIOD_TOO_LONG).
                    남은 크레딧이 0이면 플랜을 선택할 수 없으므로 402(INSUFFICIENT_CREDITS)이고 목표를 만들지 않습니다.
                    여기서는 확인만 하고 차감하지 않습니다(차감은 플랜 선택 때). 필드 검증(400 VALIDATION_FAILED)이 먼저입니다.
                    """)
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponses.Created>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalService.create(authUser.userId(), request)));
    }

    @Operation(summary = "고정 일정 날짜별 조회",
            description = """
                    기간이 [from, to] 와 겹치는 내 목표들의 요일별 고정 일정을 실제 날짜로 펼쳐 날짜별로 묶어 돌려줍니다.
                    플랜을 고른 목표만 봅니다. 진행 중(IN_PROGRESS·PAST)은 목표 기간 안, 완료(COMPLETED)는 완료한 날까지만 나오고,
                    플랜 선택 전(PLANNING) 목표는 나오지 않습니다. 일정이 하나도 없는 날짜는 응답에 없습니다.
                    날짜 오름차순, 같은 날은 목표 생성 순입니다.
                    from·to 는 yyyy-MM-dd 이고 둘 다 필요하며, 양 끝 포함 최대 366일(to 는 from + 365일까지)입니다.
                    하나라도 없거나 from > to 이거나 366일을 넘으면 400(GOAL_INVALID_RANGE), 날짜 형식이 틀리면 400(BINDING_ERROR).
                    """)
    @GetMapping("/fixed-schedules")
    public ApiResponse<FixedScheduleResponses.Days> fixedSchedules(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(goalService.fixedSchedules(authUser.userId(), from, to));
    }

    @Operation(summary = "목표 상세 조회",
            description = "없거나 삭제된 목표는 404(GOAL_NOT_FOUND), 다른 사람의 목표는 403(FORBIDDEN).")
    @GetMapping("/{goalId}")
    public ApiResponse<GoalResponses.Detail> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId) {
        return ApiResponse.success(goalService.get(authUser.userId(), goalId));
    }

    @Operation(summary = "목표 수정",
            description = """
                    제목·기간만 바꿉니다(currentStatus 는 현재 상태 수정 API를 쓰세요).
                    진행 중 목표의 기간을 바꾸면 기간 밖 미완료 투두를 무료로 재배치합니다.
                    빈자리가 없어 옮기지 못한 투두와 완료된 투두는 원래 날짜를 유지하며 periodAdjustment에 건수를 반환합니다.
                    생략한 필드는 기존 값을 유지하고, null 을 보내면 지웁니다(title 은 null 이어도 유지).
                    진행 중인 목표의 startDate·endDate 를 null 로 보내면 400(GOAL_INVALID_INPUT). 응답은 상세 조회와 같습니다.
                    지난 목표(PAST)는 409(GOAL_NOT_EDITABLE), 기간이 잘못되면 400(GOAL_INVALID_PERIOD), 404 / 403 은 상세와 같습니다.
                    기간이 365일(시작일 포함)을 넘으면 400(GOAL_PERIOD_TOO_LONG).
                    """)
    @PatchMapping("/{goalId}")
    public ApiResponse<GoalResponses.Detail> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId, @Valid @RequestBody UpdateGoalRequest request) {
        return ApiResponse.success(goalService.update(authUser.userId(), goalId, request));
    }

    @Operation(summary = "현재 상태 수정",
            description = """
                    AI 플랜을 적용한(aiPlanApplied) 목표의 currentStatus 만 바꿉니다. 다른 필드는 건드리지 않습니다. null 이면 지웁니다.
                    플랜을 선택하지 않은 목표는 409(GOAL_AI_PLAN_NOT_APPLIED),
                    지난 목표(PAST)·완료한 목표(COMPLETED)는 409(GOAL_NOT_EDITABLE), 404 / 403 은 상세와 같습니다.
                    """)
    @PatchMapping("/{goalId}/current-status")
    public ApiResponse<CurrentStatusResponse> updateCurrentStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId, @Valid @RequestBody UpdateCurrentStatusRequest request) {
        return ApiResponse.success(goalService.updateCurrentStatus(authUser.userId(), goalId, request.currentStatus()));
    }

    @Operation(summary = "목표 완료",
            description = """
                    AI 플랜을 적용한 진행 중(IN_PROGRESS) 목표를 COMPLETED 로 바꿉니다. 응답은 상세 조회와 같습니다.
                    완료한 목표는 종료일이 지나도 PAST 가 되지 않고, 수정·투두 추가·미루기·재배치를 할 수 없습니다. 남은 AI 투두는 지우지 않습니다.
                    상태를 먼저 봅니다. PLANNING·이미 완료·지난 목표(PAST)는 409(GOAL_NOT_IN_PROGRESS),
                    진행 중인데 선택한 플랜이 없으면 409(GOAL_AI_PLAN_NOT_APPLIED), 404 / 403 은 상세와 같습니다.
                    """)
    @PatchMapping("/{goalId}/complete")
    public ApiResponse<GoalResponses.Detail> complete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId) {
        return ApiResponse.success(goalService.complete(authUser.userId(), goalId));
    }

    @Operation(summary = "목표 삭제",
            description = """
                    진행 중·지난 목표·완료한 목표·플랜 선택 전 목표를 삭제할 수 있습니다.
                    연결된 투두도 함께 소프트 삭제됩니다.
                    성공 시 HTTP 200, 공통 응답의 data 는 null 입니다. 404 / 403 은 상세와 같습니다.
                    """)
    @DeleteMapping("/{goalId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId) {
        goalService.delete(authUser.userId(), goalId);
        return ApiResponse.noContent();
    }
}
