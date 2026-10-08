package com.hama.domain.goal.entity;

/** 목표 리스트 status 필터. 생략하면 둘 다 조회합니다(PLANNING 은 리스트에 나오지 않습니다). */
public enum GoalListFilter {
    IN_PROGRESS, PAST
}
