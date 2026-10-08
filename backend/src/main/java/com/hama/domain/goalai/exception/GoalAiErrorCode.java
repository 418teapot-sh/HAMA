package com.hama.domain.goalai.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GoalAiErrorCode implements BaseErrorCode {
    AI_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "목표 대화 세션을 찾을 수 없습니다."),
    AI_SESSION_NOT_READY(HttpStatus.CONFLICT, "목표 초안이 아직 완성되지 않았습니다."),
    AI_SESSION_CONFIRMED(HttpStatus.CONFLICT, "이미 목표로 확정된 세션입니다."),
    AI_SESSION_TURN_LIMIT(HttpStatus.CONFLICT, "대화 횟수를 모두 사용했습니다. 새 세션으로 다시 시작해주세요."),
    AI_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "플랜을 찾을 수 없습니다."),
    AI_PLAN_ALREADY_SELECTED(HttpStatus.CONFLICT, "이미 선택된 플랜입니다."),
    AI_PLAN_OUTDATED(HttpStatus.CONFLICT, "목표 기간이 바뀌어 플랜을 적용할 수 없습니다. 플랜을 다시 만들어주세요."),
    GOAL_NOT_PLANNING(HttpStatus.CONFLICT, "플랜 선택 전(PLANNING) 목표에서만 할 수 있습니다."),
    GOAL_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 목표에서만 할 수 있습니다."),
    AI_REPLAN_INVALID_DATE(HttpStatus.BAD_REQUEST, "재배치 시작일은 목표 기간 안이어야 합니다.");

    private final HttpStatus status;
    private final String message;
}
