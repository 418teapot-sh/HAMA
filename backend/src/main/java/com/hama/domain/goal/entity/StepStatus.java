package com.hama.domain.goal.entity;

/** 마일스톤·기간 목표의 진행 상태. 언제 바뀌는지는 goals/ai 플랜 적용·투두 완료 연동 때 정합니다. */
public enum StepStatus {
    PENDING, IN_PROGRESS, COMPLETED
}
