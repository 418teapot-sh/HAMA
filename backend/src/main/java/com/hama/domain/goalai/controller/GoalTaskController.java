package com.hama.domain.goalai.controller;

import com.hama.domain.goalai.dto.GoalTaskResponses;
import com.hama.domain.goalai.service.GoalTaskService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표 할 일", description = "선택한 A/B 플랜의 반복 할 일과 할 일별 AI 투두 진행. 예전 /goals/{goalId}/tree 를 대신합니다.")
@RestController
@RequiredArgsConstructor
public class GoalTaskController {

    private final GoalTaskService service;

    @Operation(summary = "목표 할 일별 진행 조회",
            description = """
                    선택한 플랜의 tasks 를 플랜 순서대로 돌려주고, 할 일마다 그 내용으로 만든 AI 투두(삭제 제외) 수를 셉니다.
                    todoTotal·todoCompleted 는 목표 전체, weekTodoTotal·weekTodoCompleted 는 이번 주(weekStart~weekEnd, KST 월~일)입니다.
                    홈 목표 카드는 이번 주 값, 리포트 체크 목록은 전체 값을 쓰면 됩니다. 사용자가 투두 내용을 고치면 그 투두는 세지 않습니다.
                    플랜을 선택하지 않은 목표는 tasks 가 빈 배열입니다. 없는 목표 404(GOAL_NOT_FOUND), 다른 사람의 목표 403(FORBIDDEN).
                    """)
    @GetMapping("/api/v1/goals/{goalId}/tasks")
    public ApiResponse<GoalTaskResponses.TaskList> tasks(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long goalId) {
        return ApiResponse.success(service.tasks(authUser.userId(), goalId));
    }
}
