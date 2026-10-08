package com.hama.domain.goalai.dto;

import com.hama.domain.goalai.entity.GoalPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
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
            @Schema(example = "주 5일, 하루 1.5시간.") String summary,
            List<MilestoneView> milestones,
            Stats stats,
            boolean selected) {

        public static PlanView of(GoalPlan plan, PlanDetail detail) {
            return new PlanView(plan.getId(), plan.getVariant(), plan.getTitle(), plan.getSummary(),
                    detail.milestones().stream()
                            .map(m -> new MilestoneView(m.seq(), m.title(), m.startDate(), m.endDate()))
                            .toList(),
                    new Stats(plan.getTotalTodos(), plan.getAvgDailyMinutes()),
                    plan.isSelected());
        }
    }

    public record MilestoneView(int seq, String title, LocalDate startDate, LocalDate endDate) {
    }

    @Schema(description = "avgDailyMinutes 는 투두가 있는 날 하루 평균 분")
    public record Stats(int totalTodos, int avgDailyMinutes) {
    }
}
