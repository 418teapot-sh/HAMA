package com.hama.domain.calendar.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CalendarErrorCode implements BaseErrorCode {
    CALENDAR_CONVERSION_INVALID(HttpStatus.BAD_REQUEST, "전환할 카테고리와 해당 날짜·시간을 올바르게 입력해야 합니다."),
    CALENDAR_CONVERSION_NOT_ALLOWED(HttpStatus.CONFLICT, "목표 투두·고정 일정·반복 일정은 카테고리를 전환할 수 없습니다."),
    CALENDAR_INVALID_RANGE(HttpStatus.BAD_REQUEST, "from/to는 YYYY-MM-DD이며 1000~9999년 범위에서 양끝 포함 최대 366일이어야 합니다."),
    CALENDAR_INVALID_TYPES(HttpStatus.BAD_REQUEST, "types는 FIXED, PERSONAL, AI_GOAL, TASK, OTHER 중 쉼표로 구분해 지정해야 합니다."),
    CALENDAR_RESULT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "결과는 요청당 최대 10,000개입니다. 기간 또는 종류 필터를 좁혀주세요."),
    CALENDAR_NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "요청한 Accept 형식으로 응답할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
