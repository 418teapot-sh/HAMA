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

/** 목표를 큰 단계로 나눈 것. 목표 → 마일스톤 → 기간 목표(월/주) → 투두 순으로 내려갑니다. */
@Entity
@Table(name = "milestone", indexes = @Index(name = "idx_milestone_goal_seq", columnList = "goal_id,seq"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Milestone extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "milestone_id")
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    public static Milestone create(Long goalId, int seq, String title, String description,
            LocalDate startDate, LocalDate endDate) {
        if (goalId == null || seq < 1 || title == null || title.isBlank() || title.length() > 100) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_PERIOD);
        }
        Milestone milestone = new Milestone();
        milestone.goalId = goalId;
        milestone.seq = seq;
        milestone.title = title;
        milestone.description = description;
        milestone.startDate = startDate;
        milestone.endDate = endDate;
        milestone.status = StepStatus.PENDING;
        return milestone;
    }
}
