package com.hama.domain.goal.entity;

/**
 * DB 에는 PLANNING / IN_PROGRESS 만 저장합니다. PAST 는 {@link Goal#effectiveStatus} 가 종료일로 계산합니다.
 */
public enum GoalStatus {
    PLANNING, IN_PROGRESS, PAST
}
