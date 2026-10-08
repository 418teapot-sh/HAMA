package com.hama.domain.calendar.controller;

import com.hama.domain.calendar.exception.CalendarErrorCode;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalExceptionHandler;
import com.hama.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
            case MethodArgumentNotValidException validation -> common.handleValidationExceptions(validation);
            case MethodArgumentTypeMismatchException mismatch -> common.handleTypeMismatchException(mismatch);
            case ConversionFailedException conversion -> common.handleTypeMismatchException(conversion);
            case HttpMessageNotReadableException parsing -> common.handleInvalidRequestFormat(parsing);
            case MissingServletRequestPartException missingPart -> common.handleInvalidRequestFormat(missingPart);
            case NoResourceFoundException missing -> common.handleNoResource(missing);
            case HttpRequestMethodNotSupportedException method -> common.handleMethodNotSupported(method);
            case HttpMediaTypeNotSupportedException media -> common.handleWrongMediaType(media);
            case MultipartException multipart -> common.handleInvalidMultiPartFormRequest(multipart);
            case HttpMediaTypeNotAcceptableException accept -> common.handleBusinessException(
                    new BusinessException(CalendarErrorCode.CALENDAR_NOT_ACCEPTABLE, accept));
            case AccessDeniedException denied -> common.handleAccessDenied(denied);
            case AuthenticationException unauthorized -> common.handleAuthentication(unauthorized);
            default -> common.handleGeneralException(exception);
        };
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .contentType(MediaType.APPLICATION_JSON).body(response.getBody());
    }
}
