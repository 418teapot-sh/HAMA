package com.hama.domain.calendar.google.importer;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GoogleImportErrorCode implements BaseErrorCode {
    GOOGLE_IMPORT_INVALID_INPUT(HttpStatus.BAD_REQUEST, "캘린더와 양끝 포함 최대 366일의 조회 기간을 확인해주세요."),
    GOOGLE_IMPORT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "가져올 Google 일정은 최대 1,000건입니다. 기간을 줄여주세요."),
    GOOGLE_IMPORT_ACCESS_DENIED(HttpStatus.BAD_REQUEST, "Google 캘린더에 접근할 수 없습니다. 다시 연결해주세요."),
    GOOGLE_IMPORT_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "Google 캘린더를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
