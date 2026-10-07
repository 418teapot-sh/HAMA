package com.hama.domain.goal.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GoalErrorCode implements BaseErrorCode {
    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "목표를 찾을 수 없습니다."),
    GOAL_INVALID_INPUT(HttpStatus.BAD_REQUEST, "목표의 필수 정보가 올바르지 않습니다."),
    GOAL_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "시작일은 종료일보다 늦을 수 없고, 종료일은 오늘 이후여야 합니다."),
    GOAL_NOT_EDITABLE(HttpStatus.CONFLICT, "지난 목표는 수정할 수 없습니다."),
    GOAL_NOT_DELETABLE(HttpStatus.CONFLICT, "지난 목표만 삭제할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
