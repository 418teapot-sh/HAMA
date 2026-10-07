package com.hama.global.ai;

import com.hama.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * AI 호출 공통 에러코드. goals/ai 와 reviews 가 같이 쓰므로 도메인 패키지가 아니라 여기에 둡니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements BaseErrorCode {

    AI_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "AI 요청이 많아 잠시 후 다시 시도해주세요."),
    AI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요."),
    AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능이 설정되지 않았습니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
