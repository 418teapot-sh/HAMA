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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증은 됐지만 권한이 없는 요청(403). {@link JwtAuthenticationEntryPoint} 와 같은 이유로
 * 여기서 직접 ApiResponse 형식으로 내려줍니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("권한 없음: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(GlobalErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(),
                ApiResponse.error(GlobalErrorCode.FORBIDDEN, GlobalErrorCode.FORBIDDEN.getMessage()));
    }
}
