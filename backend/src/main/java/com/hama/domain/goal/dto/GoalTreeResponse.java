package com.hama.domain.goal.dto;

import com.hama.domain.goal.entity.Milestone;
import com.hama.domain.goal.entity.PeriodGoal;
import com.hama.domain.goal.entity.PeriodType;
import com.hama.domain.goal.entity.StepStatus;
import com.hama.domain.goal.repository.GoalTodoCount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record GoalTreeResponse(Long goalId, List<MilestoneNode> milestones) {

    public record MilestoneNode(Long milestoneId, int seq, String title, LocalDate startDate, LocalDate endDate,
            StepStatus status, BigDecimal progressRate, List<PeriodGoalNode> periodGoals) {
    }

    public record PeriodGoalNode(Long periodGoalId, PeriodType periodType, String title,
            LocalDate startDate, LocalDate endDate, long todoTotal, long todoCompleted) {
    }

    /** 마일스톤 진행률은 소속 기간 목표들의 투두를 합쳐 계산합니다. */
    public static GoalTreeResponse of(Long goalId, List<Milestone> milestones, List<PeriodGoal> periodGoals,
            Map<Long, GoalTodoCount> countsByPeriodGoal) {
        Map<Long, List<PeriodGoal>> byMilestone = periodGoals.stream()
                .collect(Collectors.groupingBy(PeriodGoal::getMilestoneId));

        List<MilestoneNode> nodes = milestones.stream().map(milestone -> {
            List<PeriodGoal> children = byMilestone.getOrDefault(milestone.getId(), List.of());
            GoalTodoCount milestoneCount = GoalTodoCount.EMPTY;
            List<PeriodGoalNode> childNodes = new ArrayList<>();
            for (PeriodGoal periodGoal : children) {
                GoalTodoCount count = countsByPeriodGoal.getOrDefault(periodGoal.getId(), GoalTodoCount.EMPTY);
                milestoneCount = milestoneCount.plus(count);
                childNodes.add(new PeriodGoalNode(periodGoal.getId(), periodGoal.getPeriodType(),
                        periodGoal.getTitle(), periodGoal.getStartDate(), periodGoal.getEndDate(),
                        count.total(), count.completed()));
            }
            return new MilestoneNode(milestone.getId(), milestone.getSeq(), milestone.getTitle(),
                    milestone.getStartDate(), milestone.getEndDate(), milestone.getStatus(),
                    milestoneCount.progressRate(), childNodes);
        }).toList();

        return new GoalTreeResponse(goalId, nodes);
    }
}
