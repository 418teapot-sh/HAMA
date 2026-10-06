package com.hama.domain.goal.entity;

import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.global.entity.BaseTimeEntity;
import com.hama.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 마일스톤 안의 월간·주간 목표. AI_GOAL_TASK 투두가 todo.period_goal_id 로 여기에 붙습니다. */
@Entity
@Table(name = "period_goal", indexes = @Index(name = "idx_period_goal_goal_milestone_seq",
        columnList = "goal_id,milestone_id,seq"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeriodGoal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "period_goal_id")
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "milestone_id", nullable = false)
    private Long milestoneId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "period_type", nullable = false, length = 10)
    private PeriodType periodType;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    public static PeriodGoal create(Milestone milestone, PeriodType periodType, int seq, String title,
            LocalDate startDate, LocalDate endDate) {
        if (milestone == null || milestone.getId() == null || periodType == null || seq < 1
                || title == null || title.isBlank() || title.length() > 100) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_PERIOD);
        }
        PeriodGoal periodGoal = new PeriodGoal();
        periodGoal.goalId = milestone.getGoalId();
        periodGoal.milestoneId = milestone.getId();
        periodGoal.periodType = periodType;
        periodGoal.seq = seq;
        periodGoal.title = title;
        periodGoal.startDate = startDate;
        periodGoal.endDate = endDate;
        periodGoal.status = StepStatus.PENDING;
        return periodGoal;
    }
}
