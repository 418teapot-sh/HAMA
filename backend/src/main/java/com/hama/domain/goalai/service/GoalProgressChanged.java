package com.hama.domain.goalai.service;

/** 발행하는 서비스는 목표 잠금을 보유해야 합니다. 수신은 원본 트랜잭션 안에서 동기 실행됩니다. */
public record GoalProgressChanged(Long goalId) {
}
