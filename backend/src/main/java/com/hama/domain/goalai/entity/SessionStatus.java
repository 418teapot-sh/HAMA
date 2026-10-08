package com.hama.domain.goalai.entity;

/** COLLECTING(정보 수집 중) → READY(초안 완성) → CONFIRMED(목표 생성됨). READY 에서 대화를 이어가면 COLLECTING 으로 돌아갈 수 있습니다. */
public enum SessionStatus {
    COLLECTING, READY, CONFIRMED
}
