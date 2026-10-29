package com.hama.domain.goalai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public final class GoalTaskResponses {

    private GoalTaskResponses() {
    }

    @Schema(description = "weekStart~weekEnd 는 이번 주(KST 월~일). 플랜을 선택하지 않은 목표는 tasks 가 빈 배열")
    public record TaskList(Long goalId, LocalDate weekStart, LocalDate weekEnd, List<TaskProgress> tasks) {
    }

    @Schema(description = "선택된 플랜의 반복 할 일과 그 할 일로 만든 AI 투두 수. todo* 는 전체, weekTodo* 는 이번 주")
    public record TaskProgress(String content, int timesPerWeek, int minutes,
            long todoTotal, long todoCompleted, long weekTodoTotal, long weekTodoCompleted) {
    }
}
