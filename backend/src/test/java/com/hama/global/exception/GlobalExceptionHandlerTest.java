package com.hama.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스프링 컨텍스트 없이 핸들러만 붙여서 응답 형식을 확인합니다. (DB 불필요, 빠름)
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 서버_에러는_상세_메시지를_숨기고_에러코드_기본_문구를_보낸다() throws Exception {
        mockMvc.perform(get("/test/5xx"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_API_FAILED"))
                .andExpect(jsonPath("$.error.message").value("외부 서비스 호출에 실패했습니다."));
    }

    @Test
    void 클라이언트_에러는_던질_때_넣은_메시지를_그대로_보낸다() throws Exception {
        mockMvc.perform(get("/test/4xx"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("3번 투두를 찾을 수 없습니다."));
    }

    @Test
    void 지원하지_않는_Content_Type은_415() throws Exception {
        mockMvc.perform(post("/test/json")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("NOT_SUPPORTED_MEDIA"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/5xx")
        void serverError() {
            throw new BusinessException(TestErrorCode.EXTERNAL_API_FAILED,
                    "POST https://internal-host:8443/v1/chat failed, key=sk-abc123");
        }

        @GetMapping("/test/4xx")
        void clientError() {
            throw new BusinessException(TestErrorCode.ITEM_NOT_FOUND, "3번 투두를 찾을 수 없습니다.");
        }

        @PostMapping(value = "/test/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        void json(@RequestBody String body) {
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum TestErrorCode implements BaseErrorCode {
        EXTERNAL_API_FAILED(HttpStatus.BAD_GATEWAY, "외부 서비스 호출에 실패했습니다."),
        ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다.");

        private final HttpStatus status;
        private final String message;
    }
}
