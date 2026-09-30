package com.hama.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.payment.entity.Payment;
import com.hama.domain.payment.repository.PaymentRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookIntegrationTest {

    /** application-test.yml 의 paddle.webhook-secret */
    private static final String SECRET = "test-only-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User newUser() {
        return userRepository.save(User.create("pay-" + UUID.randomUUID() + "@hama.com", "encoded", "하마"));
    }

    /** Paddle 문서의 transaction.completed 예시에서 쓰는 필드만 남겼습니다. 금액은 원 단위 문자열입니다. */
    private static String completedEvent(String transactionId, String userIdJson) {
        return completedEvent(transactionId, userIdJson, "\"2000\"");
    }

    private static String completedEvent(String transactionId, String userIdJson, String totalJson) {
        return """
                {
                  "event_id": "evt_%s",
                  "event_type": "transaction.completed",
                  "occurred_at": "2026-10-31T01:00:00.000000Z",
                  "data": {
                    "id": "%s",
                    "status": "completed",
                    "currency_code": "KRW",
                    "custom_data": { "userId": %s },
                    "billed_at": "2026-10-31T00:59:58.000000Z",
                    "details": { "totals": { "total": %s } }
                  }
                }
                """.formatted(UUID.randomUUID(), transactionId, userIdJson, totalJson);
    }

    private ResultActions sendWebhook(String body, String secret) throws Exception {
        long ts = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ":").getBytes(StandardCharsets.UTF_8));
        String h1 = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        return mockMvc.perform(post("/api/v1/payments/webhook")
                .header("Paddle-Signature", "ts=" + ts + ";h1=" + h1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean isPremium(Long userId) {
        return userRepository.findById(userId).orElseThrow().isPremium();
    }

    @Test
    void 결제_완료_웹훅이_오면_결제를_저장하고_프리미엄을_켠다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();

        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId())), SECRET).andExpect(status().isOk());

        assertThat(isPremium(user.getId())).isTrue();
        Payment payment = paymentOf(transactionId);
        assertThat(payment.getAmount()).isEqualTo(2000);
        assertThat(payment.getCurrency()).isEqualTo("KRW");
    }

    @Test
    void 금액을_읽지_못해도_프리미엄은_켜고_금액은_0_으로_저장한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();

        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId()), "\"19.99\""), SECRET)
                .andExpect(status().isOk());

        assertThat(isPremium(user.getId())).isTrue();
        assertThat(paymentOf(transactionId).getAmount()).isZero();
    }

    private Payment paymentOf(String transactionId) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getPaddleTransactionId().equals(transactionId))
                .findFirst().orElseThrow();
    }

    @Test
    void 같은_거래의_웹훅이_두_번_와도_한_번만_저장한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();
        String body = completedEvent(transactionId, String.valueOf(user.getId()));

        sendWebhook(body, SECRET).andExpect(status().isOk());
        sendWebhook(body, SECRET).andExpect(status().isOk());

        assertThat(paymentRepository.findAll().stream()
                .filter(payment -> payment.getPaddleTransactionId().equals(transactionId))
                .count()).isEqualTo(1);
    }

    @Test
    void customData_의_userId_가_문자열이어도_처리한다() throws Exception {
        User user = newUser();

        sendWebhook(completedEvent("txn_" + UUID.randomUUID(), "\"" + user.getId() + "\""), SECRET)
                .andExpect(status().isOk());

        assertThat(isPremium(user.getId())).isTrue();
    }

    @Test
    void 서명이_틀리면_401_이고_프리미엄을_켜지_않는다() throws Exception {
        User user = newUser();

        sendWebhook(completedEvent("txn_" + UUID.randomUUID(), String.valueOf(user.getId())), "forged-secret")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(isPremium(user.getId())).isFalse();
    }

    @Test
    void 사용자를_찾지_못해도_200_으로_끝내고_재전송을_막는다() throws Exception {
        String transactionId = "txn_" + UUID.randomUUID();

        sendWebhook(completedEvent(transactionId, "999999999"), SECRET).andExpect(status().isOk());

        assertThat(paymentRepository.existsByPaddleTransactionId(transactionId)).isFalse();
    }

    @Test
    void 처리하지_않는_이벤트는_200_으로_받고_넘긴다() throws Exception {
        sendWebhook("""
                { "event_id": "evt_1", "event_type": "customer.created", "data": {} }
                """, SECRET).andExpect(status().isOk());
    }
}
