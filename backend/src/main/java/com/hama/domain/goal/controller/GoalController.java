package com.hama.domain.goal.controller;

import com.hama.domain.goal.dto.CreateGoalRequest;
import com.hama.domain.goal.dto.GoalResponses;
import com.hama.domain.goal.dto.GoalTreeResponse;
import com.hama.domain.goal.dto.UpdateGoalRequest;
import com.hama.domain.goal.service.GoalService;
import com.hama.global.auth.AuthUser;
import com.hama.domain.goal.entity.GoalListFilter;
import com.hama.global.response.ApiResponse;
import com.hama.global.response.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표", description = """
        목표 직접 생성·조회·수정·삭제. 종료일이 오늘(KST)보다 이전이면 status 는 PAST(지난 목표)입니다.
        진행률(progressRate) = 완료된 AI_GOAL_TASK ÷ 전체 AI_GOAL_TASK × 100, 투두가 없으면 0.0.
        """)
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @Operation(summary = "목표 리스트 조회",
            description = """
                    status: IN_PROGRESS(진행 중, 마감 가까운 순) | PAST(지난 목표, 최근 종료 순),
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
                    AI 없이 직접 입력한 목표를 IN_PROGRESS 로 생성합니다.
                    시작일 > 종료일이거나 종료일이 오늘 이전이면 400(GOAL_INVALID_PERIOD), 필드 검증 실패는 400(VALIDATION_FAILED).
                    """)
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponses.Created>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalService.create(authUser.userId(), request)));
    }

    @Operation(summary = "목표 상세 조회",
            description = "없거나 삭제된 목표는 404(GOAL_NOT_FOUND), 다른 사람의 목표는 403(FORBIDDEN).")
    @GetMapping("/{goalId}")
    public ApiResponse<GoalResponses.Detail> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId) {
        return ApiResponse.success(goalService.get(authUser.userId(), goalId));
    }

    @Operation(summary = "목표 계층 트리 조회",
            description = """
                    목표 → 마일스톤(seq 순) → 기간 목표(월/주, seq 순). 기간 목표마다 AI_GOAL_TASK 전체·완료 개수,
                    마일스톤마다 소속 기간 목표 기준 진행률을 담습니다. 플랜을 적용하지 않은 목표는 milestones 가 빈 배열입니다.
                    404 / 403 은 상세와 같습니다.
                    """)
    @GetMapping("/{goalId}/tree")
    public ApiResponse<GoalTreeResponse> tree(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId) {
        return ApiResponse.success(goalService.tree(authUser.userId(), goalId));
    }

    @Operation(summary = "목표 수정",
            description = """
                    변경할 필드만 보냅니다. 생략한 필드는 기존 값을 유지하고, null 을 보내면 지웁니다(title 은 null 이어도 유지).
                    진행 중인 목표의 startDate·endDate 를 null 로 보내면 400(GOAL_INVALID_INPUT). 응답은 상세 조회와 같습니다.
                    지난 목표(PAST)는 409(GOAL_NOT_EDITABLE), 기간이 잘못되면 400(GOAL_INVALID_PERIOD), 404 / 403 은 상세와 같습니다.
                    """)
    @PatchMapping("/{goalId}")
    public ApiResponse<GoalResponses.Detail> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId, @Valid @RequestBody UpdateGoalRequest request) {
        return ApiResponse.success(goalService.update(authUser.userId(), goalId, request));
    }

    @Operation(summary = "목표 삭제",
            description = """
                    지난 목표(PAST)와 플랜 선택 전(PLANNING) 목표만 삭제할 수 있고, 진행 중(IN_PROGRESS)이면 409(GOAL_NOT_DELETABLE)입니다.
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
