package com.hama.domain.checkin.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CheckinErrorCode implements BaseErrorCode {
    CHECKIN_INVALID_INPUT(HttpStatus.BAD_REQUEST, "체크인 입력이 올바르지 않습니다."),
    CHECKIN_INVALID_TIME(HttpStatus.BAD_REQUEST, "기록 시각은 유효한 과거 또는 현재 KST 시각이어야 합니다."),
    CHECKIN_ALREADY_EXISTS(HttpStatus.CONFLICT, "시작·종료 체크인은 목표마다 한 번만 기록할 수 있습니다."),
    CHECKIN_GOAL_NOT_STARTED(HttpStatus.CONFLICT, "계획 중인 목표에는 체크인할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
