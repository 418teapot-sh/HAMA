package com.hama.domain.user.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    /** 401 이면 프론트가 토큰 만료로 보고 재발급을 시도하므로 400 입니다. */
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다."),
    TOO_MANY_PASSWORD_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "비밀번호 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),
    ;

    private final HttpStatus status;
    private final String message;
}
