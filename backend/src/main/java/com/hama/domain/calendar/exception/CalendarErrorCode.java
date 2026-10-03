package com.hama.domain.calendar.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CalendarErrorCode implements BaseErrorCode {
    CALENDAR_INVALID_RANGE(HttpStatus.BAD_REQUEST, "from/to는 YYYY-MM-DD이며 1000~9999년 범위에서 양끝 포함 최대 366일이어야 합니다."),
    CALENDAR_INVALID_TYPES(HttpStatus.BAD_REQUEST, "types는 FIXED, PERSONAL, AI_GOAL, TASK 중 쉼표로 구분해 지정해야 합니다.");

    private final HttpStatus status;
    private final String message;
}
