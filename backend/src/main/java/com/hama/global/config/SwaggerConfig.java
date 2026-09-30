package com.hama.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * OpenAPI 스펙: http://localhost:8080/v3/api-docs (프론트가 여기서 API 타입을 생성합니다)
 *
 * <p>Swagger UI 우측 상단 Authorize 에 로그인 응답의 accessToken 을 넣으면
 * 모든 요청에 {@code Authorization: Bearer ...} 가 붙습니다. ("Bearer " 는 빼고 토큰만 넣습니다)
 */
@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("HAMA API")
                .version("v1")
                .description("""
                        HAMA API 문서입니다.

                        - 성공: `{ "success": true, "data": <T>, "error": null, "traceId": ... }`
                        - 실패: `{ "success": false, "data": null, "error": { "code", "message", "fields" }, "traceId": ... }`
                        - `error.fields` 는 검증 실패일 때만 `{ "필드명": "메시지" }`, 그 외에는 null 입니다.
                        - 문제가 생기면 응답의 `traceId`(또는 `X-Trace-Id` 헤더)를 백엔드에 알려주세요. 그 값으로 로그를 찾습니다.

                        **인증**: 로그인 응답의 `accessToken` 을 Authorize 에 넣으세요. 리프레시 토큰은 HttpOnly 쿠키라 직접 다루지 않습니다.
                        """);

        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme))
                // 기본값으로 모든 API 에 자물쇠를 겁니다. 공개 API 는 컨트롤러에서 @SecurityRequirements 로 풉니다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
