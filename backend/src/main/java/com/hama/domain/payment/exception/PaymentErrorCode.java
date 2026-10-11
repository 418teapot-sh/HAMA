package com.hama.domain.payment.exception;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {

    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "웹훅 서명이 올바르지 않습니다."),
    /** Paddle 웹훅 전용. 5xx 라서 Paddle 이 재전송합니다. */
    UNKNOWN_PRICE(HttpStatus.INTERNAL_SERVER_ERROR, "설정에 없는 가격의 결제입니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
