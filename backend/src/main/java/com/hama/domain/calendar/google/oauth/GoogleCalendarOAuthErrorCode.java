package com.hama.domain.calendar.google.oauth;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GoogleCalendarOAuthErrorCode implements BaseErrorCode {
    GOOGLE_IMPORT_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "Google 캘린더 가져오기가 설정되지 않았습니다."),
    GOOGLE_IMPORT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Google 가져오기 세션을 찾을 수 없습니다."),
    GOOGLE_IMPORT_FORBIDDEN(HttpStatus.FORBIDDEN, "접근할 수 없는 Google 가져오기 세션 또는 캘린더입니다."),
    GOOGLE_IMPORT_SESSION_EXPIRED(HttpStatus.GONE, "Google 가져오기 세션이 만료되었습니다. 다시 연결해주세요."),
    GOOGLE_IMPORT_AUTH_INVALID(HttpStatus.BAD_REQUEST, "Google 인증 요청을 확인할 수 없습니다. 다시 연결해주세요."),
    GOOGLE_IMPORT_NOT_AUTHORIZED(HttpStatus.CONFLICT, "Google 인증을 먼저 완료해주세요."),
    GOOGLE_IMPORT_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "Google 캘린더에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
