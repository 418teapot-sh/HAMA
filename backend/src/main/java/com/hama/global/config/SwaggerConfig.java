package com.hama.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * OpenAPI 스펙: http://localhost:8080/v3/api-docs (프론트가 여기서 API 타입을 생성합니다)
 *
 * <p>인증 방식(SecurityScheme)은 인증 이슈에서 쿠키 방식 설계가 정해지면 추가합니다.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("HAMA API")
                .version("v1")
                .description("""
                        HAMA API 문서입니다.

                        - 모든 응답은 `{ "success": ..., "data": ..., "error": ..., "traceId": ... }` 형식입니다.
                        - 문제가 생기면 응답의 `traceId`(또는 `X-Trace-Id` 헤더)를 백엔드에 알려주세요. 그 값으로 로그를 찾습니다.
                        """);

        return new OpenAPI().info(info);
    }
}
