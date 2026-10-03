package com.hama.domain.schedule.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements BaseErrorCode {
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    SCHEDULE_INVALID_INPUT(HttpStatus.BAD_REQUEST, "일정의 필수 정보가 올바르지 않습니다."),
    SCHEDULE_INVALID_TIME(HttpStatus.BAD_REQUEST, "시작·종료 일시는 1000~9999년 범위이며 시작보다 늦게 종료해야 합니다."),
    SCHEDULE_INVALID_ALL_DAY(HttpStatus.BAD_REQUEST, "종일 일정은 시작일 00:00부터 마지막 날 다음 날 00:00까지 입력해야 합니다."),
    SCHEDULE_INVALID_REPEAT_RULE(HttpStatus.BAD_REQUEST, "반복 규칙의 형식 또는 지원 범위가 올바르지 않습니다."),
    SCHEDULE_REPEAT_START_MISMATCH(HttpStatus.BAD_REQUEST, "반복 요일에 시작 요일이 포함되어야 하며 반복 종료는 시작 이전일 수 없습니다."),
    SCHEDULE_MEMO_TOO_LONG(HttpStatus.BAD_REQUEST, "메모는 UTF-8 기준 65535바이트 이하여야 합니다.");

    private final HttpStatus status;
    private final String message;
}
