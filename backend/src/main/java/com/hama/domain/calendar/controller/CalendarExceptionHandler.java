package com.hama.domain.calendar.controller;

import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalExceptionHandler;
import com.hama.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 파일 Accept 헤더와 관계없이 오류는 JSON. 본문·코드·로그는 기존 공통 처리기에 위임합니다. */
@RestControllerAdvice(assignableTypes = CalendarController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CalendarExceptionHandler {

    private final GlobalExceptionHandler common;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handle(Exception exception) {
        ResponseEntity<ApiResponse<Void>> response = switch (exception) {
            case BusinessException business -> common.handleBusinessException(business);
            case AccessDeniedException denied -> common.handleAccessDenied(denied);
            case AuthenticationException unauthorized -> common.handleAuthentication(unauthorized);
            default -> common.handleGeneralException(exception);
        };
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .contentType(MediaType.APPLICATION_JSON).body(response.getBody());
    }
}
