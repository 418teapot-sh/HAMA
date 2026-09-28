package com.hama.global.response;

import com.hama.global.exception.BaseErrorCode;
import java.util.Map;
import org.slf4j.MDC;

/**
 * 모든 API 응답의 공통 형식입니다. 프론트는 /v3/api-docs 로 이 형식의 타입을 생성합니다.
 *
 * <ul>
 *   <li>성공: {@code { success: true,  data: <T>,  error: null, traceId }}</li>
 *   <li>실패: {@code { success: false, data: null, error: { code, message, fields }, traceId }}</li>
 * </ul>
 *
 * <p>실패일 때 {@code data} 는 항상 null 입니다. 에러 정보는 전부 {@code error} 안에만 담습니다.
 * 규칙에 예외가 있으면 프론트가 에러 처리를 경우마다 따로 짜야 하고, 생성된 타입과도 어긋납니다.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, currentTraceId());
    }

    /** 데이터 없이 성공만 알리고 싶을 때 (예: DELETE). success(data)와 이름이 겹치면 record accessor와 충돌하므로 별도 이름 사용. */
    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(true, null, null, currentTraceId());
    }

    public static ApiResponse<Void> error(BaseErrorCode errorCode, String message) {
        return error(errorCode.name(), message);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message, null), currentTraceId());
    }

    /** 검증 실패 전용. 필드별 메시지를 {@code error.fields} 에 담습니다. */
    public static ApiResponse<Void> validationError(BaseErrorCode errorCode, Map<String, String> fields) {
        return new ApiResponse<>(false, null,
                new ErrorDetail(errorCode.name(), errorCode.getMessage(), fields), currentTraceId());
    }

    /** TraceIdFilter가 MDC에 넣어둔 값을 그대로 응답에 실어줍니다. */
    private static String currentTraceId() {
        return MDC.get("traceId");
    }

    /**
     * @param fields 검증 실패일 때만 {@code { "필드명": "메시지" }}, 그 외에는 null.
     *               한 필드가 여러 검증을 어기면 메시지를 ", " 로 이어 붙입니다.
     */
    public record ErrorDetail(String code, String message, Map<String, String> fields) {
    }
}
