package com.hama.domain.goalai.dto;

import com.hama.domain.goal.entity.GoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record SelectResponse(Long goalId, GoalStatus status, Created created, Placement placement) {

    public record Created(int milestones, int periodGoals, int todos) {
    }

    @Schema(description = "placed 는 빈 시간에 넣은 투두 수, unplaced 는 빈 시간이 없어 날짜만 둔(시간 없는) 투두 수")
    public record Placement(int placed, int unplaced, List<UnplacedTodo> unplacedTodos) {
    }

    public record UnplacedTodo(String content, LocalDate todoDate) {
    }
}
