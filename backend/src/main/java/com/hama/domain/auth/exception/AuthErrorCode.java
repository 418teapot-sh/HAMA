package com.hama.domain.auth.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    /** 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않습니다(가입 여부 유추 방지). */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    /** 로그인과 탈퇴의 비밀번호 확인이 함께 씁니다(PasswordAttemptLimiter). */
    TOO_MANY_PASSWORD_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "비밀번호 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다. 다시 로그인해주세요."),
    ;

    private final HttpStatus status;
    private final String message;
}
