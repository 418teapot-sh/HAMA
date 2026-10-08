package com.hama.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class GoalResponses {

    private GoalResponses() {
    }

    public record Created(Long goalId, GoalStatus status) {
    }

    public record Detail(Long goalId, String title, String description, String metricName, String unit,
            BigDecimal startValue, BigDecimal targetValue, BigDecimal weeklyAvailableHours, String currentLevel,
            LocalDate startDate, LocalDate endDate, GoalStatus status, BigDecimal progressRate,
            String realityVerdict, String realityComment, String resultStatus,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt) {

        public static Detail from(Goal goal, LocalDate today, BigDecimal progressRate) {
            return new Detail(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getMetricName(),
                    goal.getUnit(), goal.getStartValue(), goal.getTargetValue(), goal.getWeeklyAvailableHours(),
                    goal.getCurrentLevel(), goal.getStartDate(), goal.getEndDate(), goal.effectiveStatus(today),
                    progressRate, goal.getRealityVerdict(), goal.getRealityComment(), goal.getResultStatus(),
                    goal.getCreatedAt());
        }
    }

    public record Summary(Long goalId, String title, GoalStatus status, LocalDate startDate, LocalDate endDate,
            BigDecimal progressRate, String resultStatus) {

        public static Summary from(Goal goal, LocalDate today, BigDecimal progressRate) {
            return new Summary(goal.getId(), goal.getTitle(), goal.effectiveStatus(today), goal.getStartDate(),
                    goal.getEndDate(), progressRate, goal.getResultStatus());
        }
    }
}
