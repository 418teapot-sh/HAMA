package com.hama.domain.todo.entity;

/** 생성 주체. 기존 목표 투두의 출처는 추측하지 않습니다. */
public enum TodoOrigin {
    MANUAL, AI, LEGACY_UNKNOWN;

    public Boolean aiGenerated() {
        return this == LEGACY_UNKNOWN ? null : this == AI;
    }
}
