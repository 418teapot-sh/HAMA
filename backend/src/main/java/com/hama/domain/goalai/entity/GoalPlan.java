package com.hama.domain.goalai.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 펼친 플랜(마일스톤·주간 목표·투두)은 detailJson 에 들고 있고, 변환은 서비스의 GoalAiJson 이 합니다. */
@Entity
@Table(name = "goal_plan", indexes = @Index(name = "idx_goal_plan_goal", columnList = "goal_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalPlan extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(nullable = false, length = 1)
    private String variant;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false, length = 255)
    private String summary;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PlanPreference preference;

    @Column(name = "detail_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String detailJson;

    @Column(name = "total_todos", nullable = false)
    private int totalTodos;

    @Column(name = "avg_daily_minutes", nullable = false)
    private int avgDailyMinutes;

    @Column(nullable = false)
    private boolean selected;

    public static GoalPlan create(Long goalId, String variant, String title, String summary,
            PlanPreference preference, String detailJson, int totalTodos, int avgDailyMinutes) {
        GoalPlan plan = new GoalPlan();
        plan.goalId = goalId;
        plan.variant = variant;
        plan.title = title;
        plan.summary = summary;
        plan.preference = preference;
        plan.detailJson = detailJson;
        plan.totalTodos = totalTodos;
        plan.avgDailyMinutes = avgDailyMinutes;
        plan.selected = false;
        return plan;
    }

    public void select() {
        this.selected = true;
    }
}
