package com.hama.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalFixedSchedule;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goalai.dto.ReplanResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GoalResponses {

    private GoalResponses() {
    }

    public record Created(Long goalId, GoalStatus status) {
    }

    public record Detail(Long goalId, String title,
            @Schema(description = "목표를 만들 때의 상태. 이후 바뀌지 않으며 종료 리뷰의 비교 시작점입니다")
            String initialStatus,
            String currentStatus, LocalDate startDate, LocalDate endDate,
            GoalStatus status, BigDecimal progressRate, String resultStatus,
            @Schema(description = "A/B 플랜 생성·선택으로 만들어진 목표인지 여부")
            boolean aiPlanApplied,
            List<FixedScheduleView> fixedSchedules,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,
            @Schema(description = "기간 수정 시 투두 재배치 결과. 기간 변경이 없으면 null") ReplanResponse periodAdjustment) {

        public static Detail from(Goal goal, LocalDate today, BigDecimal progressRate, boolean aiPlanApplied,
                List<GoalFixedSchedule> fixedSchedules) {
            return new Detail(goal.getId(), goal.getTitle(), goal.getInitialStatus(), goal.getCurrentStatus(),
                    goal.getStartDate(),
                    goal.getEndDate(), goal.effectiveStatus(today), progressRate, goal.getResultStatus(),
                    aiPlanApplied, fixedSchedules.stream().map(FixedScheduleView::from).toList(),
                    goal.getCreatedAt(), null);
        }

        public Detail withPeriodAdjustment(ReplanResponse adjustment) {
            return new Detail(goalId, title, initialStatus, currentStatus, startDate, endDate, status, progressRate,
                    resultStatus, aiPlanApplied, fixedSchedules, createdAt, adjustment);
        }
    }

    public record FixedScheduleView(DayOfWeek dayOfWeek, String content) {
        public static FixedScheduleView from(GoalFixedSchedule schedule) {
            return new FixedScheduleView(schedule.getDayOfWeek(), schedule.getContent());
        }
    }

    public record Summary(Long goalId, String title, GoalStatus status, LocalDate startDate, LocalDate endDate,
            BigDecimal progressRate, String resultStatus,
            @Schema(description = "A/B 플랜 생성·선택으로 만들어진 목표인지 여부")
            boolean aiPlanApplied) {

        public static Summary from(Goal goal, LocalDate today, BigDecimal progressRate, boolean aiPlanApplied) {
            return new Summary(goal.getId(), goal.getTitle(), goal.effectiveStatus(today), goal.getStartDate(),
                    goal.getEndDate(), progressRate, goal.getResultStatus(), aiPlanApplied);
        }
    }
}
