package com.hama.domain.goalai.dto;

import com.hama.domain.goalai.entity.GoalPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class PlanResponses {

    private PlanResponses() {
    }

    public record PlanList(List<PlanView> plans) {
    }

    public record PlanView(
            Long planId,
            @Schema(example = "A") String variant,
            @Schema(example = "여유형") String title,
            @Schema(example = "매일 영어 단어 30개씩 외우기 / 일주일에 모의고사 1번 보기") String summary,
            List<TaskView> tasks,
            Stats stats,
            boolean selected) {

        public static PlanView of(GoalPlan plan, PlanDetail detail) {
            return new PlanView(plan.getId(), plan.getVariant(), plan.getTitle(), plan.getSummary(),
                    detail.tasks().stream()
                            .map(t -> new TaskView(t.content(), t.timesPerWeek(), t.minutes()))
                            .toList(),
                    new Stats(plan.getTotalTodos(), plan.getAvgDailyMinutes()),
                    plan.isSelected());
        }
    }

    @Schema(description = "마일스톤 없이 평평한 반복 할 일. timesPerWeek 는 1주일에 하는 횟수(1~7)")
    public record TaskView(String content, int timesPerWeek, int minutes) {
    }

    @Schema(description = "avgDailyMinutes 는 투두가 있는 날 하루 평균 분")
    public record Stats(int totalTodos, int avgDailyMinutes) {
    }
}
