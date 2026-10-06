package com.hama.domain.goal.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** AI_GOAL_TASK 개수 집계. 진행률 = 완료 ÷ 전체 × 100 (소수 첫째 자리, 투두가 없으면 0.0). */
public record GoalTodoCount(long total, long completed) {

    public static final GoalTodoCount EMPTY = new GoalTodoCount(0, 0);

    public GoalTodoCount plus(GoalTodoCount other) {
        return new GoalTodoCount(total + other.total, completed + other.completed);
    }

    public BigDecimal progressRate() {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(completed * 100)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
