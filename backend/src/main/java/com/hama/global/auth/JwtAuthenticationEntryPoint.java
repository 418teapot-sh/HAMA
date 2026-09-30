package com.hama.global.auth;

import com.hama.global.exception.GlobalErrorCode;
import com.hama.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증되지 않은 요청(401). Security 필터 단계에서 걸리므로 GlobalExceptionHandler 를 타지 않아,
 * 여기서 직접 ApiResponse 형식으로 내려줍니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("인증 실패: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(GlobalErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(),
                ApiResponse.error(GlobalErrorCode.UNAUTHORIZED, GlobalErrorCode.UNAUTHORIZED.getMessage()));
    }
}
