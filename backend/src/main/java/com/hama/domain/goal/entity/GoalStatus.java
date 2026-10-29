package com.hama.domain.goal.entity;

/**
 * DB 에는 PLANNING / IN_PROGRESS / COMPLETED 만 저장합니다. PAST 는 {@link Goal#effectiveStatus} 가 종료일로 계산합니다.
 * COMPLETED 는 사용자가 진행 중인 목표를 직접 완료한 상태라 종료일이 지나도 PAST 가 되지 않습니다.
 */
public enum GoalStatus {
    PLANNING, IN_PROGRESS, COMPLETED, PAST
}
