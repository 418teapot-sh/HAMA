package com.hama.domain.calendar.google.oauth;

import com.hama.global.exception.GlobalErrorCode;
import com.hama.global.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Malformed OAuth payloads must not appear in parser/validation logs with a code or state. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = GoogleCalendarOAuthController.class)
public class GoogleCalendarOAuthExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> malformedAuthorization() {
        return ResponseEntity.badRequest().body(ApiResponse.error(GlobalErrorCode.VALIDATION_FAILED,
                "Google 인증 요청 형식이 잘못되었습니다."));
    }
}
