package com.hama.domain.todo.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TodoErrorCode implements BaseErrorCode {
    TODO_GOAL_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 목표의 투두만 생성하거나 미룰 수 있습니다."),
    TODO_INVALID_GOAL_PERIOD(HttpStatus.BAD_REQUEST, "투두 날짜는 목표와 선택한 기간 목표의 기간 안이어야 합니다."),
    TODO_PERIOD_GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "기간 목표를 찾을 수 없습니다."),
    TODO_PERIOD_GOAL_MISMATCH(HttpStatus.BAD_REQUEST, "기간 목표가 지정한 목표에 속하지 않습니다."),
    TODO_POSTPONE_NO_SLOT(HttpStatus.CONFLICT, "탐색 기간 안에 미룰 수 있는 빈 시간이 없습니다."),
    TODO_POSTPONE_TIME_CONFLICT(HttpStatus.CONFLICT, "지정한 시간이 다른 일정 또는 투두와 겹칩니다."),
    TODO_POSTPONE_OUTSIDE_GOAL(HttpStatus.BAD_REQUEST, "미룰 날짜는 오늘 이후(포함)이면서 큰 목표 기간 안이어야 합니다."),
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "투두를 찾을 수 없습니다."),
    TODO_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 투두입니다."),
    TODO_INVALID_INPUT(HttpStatus.BAD_REQUEST, "투두의 필수 정보가 올바르지 않습니다."),
    TODO_INVALID_TIME(HttpStatus.BAD_REQUEST, "시작·종료 시간을 함께 입력하고 시작 시간보다 늦게 종료해야 합니다."),
    TODO_INVALID_POSTPONE_DATE(HttpStatus.BAD_REQUEST, "기존 투두 날짜보다 뒤의 날짜로 미뤄야 합니다."),
    TODO_NOTE_TOO_LONG(HttpStatus.BAD_REQUEST, "메모는 UTF-8 기준 65535바이트 이하여야 합니다.");

    private final HttpStatus status;
    private final String message;
}
