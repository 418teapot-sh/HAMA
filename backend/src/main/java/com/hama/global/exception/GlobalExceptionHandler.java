package com.hama.global.exception;

import com.hama.global.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        BaseErrorCode errorCode = e.getErrorCode();
        HttpStatus status = errorCode.getStatus();

        if (status.is5xxServerError()) {
            // 5xx 는 우리 잘못이라 원인을 남겨야 고칠 수 있습니다. 여기서 스택을 안 남기면
            // 서버에 남는 증거가 에러코드 한 줄뿐이라 무엇이 실패했는지 알 수 없습니다.
            // (원인이 실리려면 던지는 쪽이 BusinessException(code, cause) 를 써야 합니다)
            log.error("[{}] {}", errorCode.name(), e.getMessage(), e);
        } else if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            log.warn("[{}] {}", errorCode.name(), e.getMessage());
        } else {
            log.info("[{}] {}", errorCode.name(), e.getMessage());
        }

        // 5xx 는 상세 메시지를 클라이언트에 보내지 않고 에러코드의 기본 문구만 보냅니다.
        // 5xx 메시지에는 외부 API(라이너) 호출 실패나 DB 연결 실패처럼 내부 호스트·엔드포인트·
        // 키 조각이 섞이기 쉽습니다. 상세는 위에서 로그(스택 포함)로 남겼으니 traceId 로 찾으면 됩니다.
        // 4xx 는 사용자가 고쳐야 하는 내용이라 던질 때 넣은 메시지를 그대로 보냅니다.
        String clientMessage = status.is5xxServerError() ? errorCode.getMessage() : e.getMessage();

        return ResponseEntity.status(status)
                .body(ApiResponse.error(errorCode, clientMessage));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException e) {
        log.info("[VALIDATION_FAILED] {}", e.getMessage());

        // 한 필드가 @NotBlank 와 @Size 를 동시에 어기면 메시지가 둘입니다. put 이면 나중 것만
        // 남고 어느 쪽이 남을지는 실행마다 다릅니다. 둘 다 보여줘야 사용자가 한 번에 고칩니다.
        // 필드별 메시지는 data 에 { "필드명": "메시지" } 로 담습니다. 프론트는 여기서 폼 에러를 그립니다.
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.merge(
                        error.getField(), error.getDefaultMessage(),
                        (existing, added) -> existing + ", " + added));

        return ResponseEntity.status(GlobalErrorCode.VALIDATION_FAILED.getStatus())
                .body(new ApiResponse<>(false, fieldErrors,
                        new ApiResponse.ErrorDetail(GlobalErrorCode.VALIDATION_FAILED.name(),
                                GlobalErrorCode.VALIDATION_FAILED.getMessage()),
                        MDC.get("traceId")));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            ConversionFailedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(Exception e) {
        log.info("[BINDING_ERROR] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "BINDING_ERROR", "요청 파라미터 형식이 잘못되었습니다.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestFormat(Exception e) {
        log.info("[PARSING_ERROR] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "PARSING_ERROR", "요청 형식이 잘못되었습니다.");
    }

    /**
     * 없는 경로입니다. 400 이 아니라 <b>404</b> 여야 합니다. 400 은 "요청 본문이 잘못됐다" 는
     * 뜻이라, 경로 오타에 그걸 돌려주면 호출자가 엉뚱한 데를 봅니다.
     *
     * <p>정적 파일이 없을 때도 여기로 옵니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.info("[NO_RESOURCE_FOUND] {}", e.getMessage());
        return errorResponse(HttpStatus.NOT_FOUND, "NO_RESOURCE_FOUND",
                "존재하지 않는 API 입니다. URL 을 다시 확인해주세요.");
    }

    /**
     * 경로는 있는데 메서드가 다릅니다. <b>405</b> 이고, RFC 상 {@code Allow} 헤더로
     * 허용 메서드를 알려줘야 합니다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.info("[METHOD_NOT_ALLOWED] {}", e.getMessage());

        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            headers.setAllow(supported);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(new ApiResponse<>(false, null,
                        new ApiResponse.ErrorDetail("METHOD_NOT_ALLOWED",
                                "이 URL 에서 지원하지 않는 HTTP 메서드입니다."),
                        MDC.get("traceId")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWrongMediaType(Exception e) {
        log.info("[NOT_SUPPORTED_MEDIA] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "NOT_SUPPORTED_MEDIA", "허용하지 않는 미디어타입입니다.");
    }

    /**
     * 잘못된 multipart 요청입니다.
     *
     * <p>파일 업로드 기능을 붙일 때는 이 핸들러보다 먼저 {@code MaxUploadSizeExceededException}
     * 전용 핸들러(413)를 추가하세요. 그건 {@link MultipartException} 을 상속해서, 없으면 여기에
     * 흡수되어 "용량 초과" 대신 "잘못된 요청" 이라는 엉뚱한 안내가 나갑니다.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidMultiPartFormRequest(Exception e) {
        log.info("[INVALID_MULTIPART] {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "잘못된 multipart/form-data 요청입니다.");
    }

    /**
     * 위에서 처리하지 못한 나머지 예외는 전부 500 으로 보냅니다.
     *
     * <p>⚠️ 인증을 붙일 때 주의하세요. 이 핸들러는 {@code Exception} 을 통째로 잡아서
     * Spring Security 의 {@code AccessDeniedException}(403), {@code AuthenticationException}(401)
     * 까지 먹습니다. 컨트롤러·서비스 안에서 던져진 경우(예: {@code @PreAuthorize} 거절)
     * 권한 없음이 500 "예상치 못한 오류" 로 나갑니다.
     * 인증 이슈에서 이 두 예외 전용 핸들러를 <b>먼저</b> 추가해야 합니다.
     * (필터 단계에서 거절된 요청은 여기까지 오지 않고 Security 의 EntryPoint / AccessDeniedHandler 가 처리합니다)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        log.error("[INTERNAL_SERVER_ERROR] {}", e.getMessage(), e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "예상치 못한 오류가 발생했습니다.");
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(false, null, new ApiResponse.ErrorDetail(code, message), MDC.get("traceId")));
    }
}
