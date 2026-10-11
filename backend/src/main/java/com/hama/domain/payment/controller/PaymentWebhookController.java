package com.hama.domain.payment.controller;

import com.hama.domain.payment.service.PaymentService;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paddle 이 호출하는 웹훅입니다. 프론트가 부르는 API 가 아니라서 Swagger 에서 숨깁니다.
 * 토큰 없이 들어오므로 SecurityConfig 에서 공개 경로로 열고, 대신 서명으로 Paddle 인지 확인합니다.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    /** 서명은 받은 바이트 그대로 계산해야 해서 본문을 문자열·객체로 바꾸지 않고 byte[] 로 받습니다. */
    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(@RequestHeader(name = "Paddle-Signature", required = false) String signature,
                                     @RequestBody byte[] rawBody) {
        paymentService.handleWebhook(signature, rawBody);
        return ApiResponse.noContent();
    }
}
