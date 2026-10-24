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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PaymentWebhookIntegrationTest {

    /** application-test.yml 의 paddle.webhook-secret */
    private static final String SECRET = "test-only-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentRepository paymentRepository;

    private User newUser() {
        return userRepository.save(User.create("pay-" + UUID.randomUUID() + "@hama.com", "encoded", "하마", false));
    }

    /** 5회권 1개. 가격 ID 는 application-test.yml 의 paddle.credit-price-ids */
    private static final String FIVE_CREDITS = """
            [{ "price": { "id": "pri_test_5" }, "quantity": 1 }]""";

    /** Paddle 문서의 transaction.completed 예시에서 쓰는 필드만 남겼습니다. 금액은 원 단위 문자열입니다. */
    private static String completedEvent(String transactionId, String userIdJson) {
        return completedEvent(transactionId, userIdJson, "\"4950\"", FIVE_CREDITS);
    }

    private static String completedEvent(String transactionId, String userIdJson, String totalJson, String itemsJson) {
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
                    "items": %s,
                    "details": { "totals": { "total": %s } }
                  }
                }
                """.formatted(UUID.randomUUID(), transactionId, userIdJson, itemsJson, totalJson);
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

    private int credits(Long userId) {
        return userRepository.findById(userId).orElseThrow().getCredits();
    }

    @Test
    void 결제_완료_웹훅이_오면_결제를_저장하고_산_만큼_크레딧을_충전한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();

        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId())), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
        Payment payment = paymentOf(transactionId);
        assertThat(payment.getAmount()).isEqualTo(4950);
        assertThat(payment.getCurrency()).isEqualTo("KRW");
        assertThat(payment.getCredits()).isEqualTo(5);
    }

    @Test
    void 여러_상품과_수량을_합쳐서_충전한다() throws Exception {
        User user = newUser();
        String items = """
                [{ "price": { "id": "pri_test_12" }, "quantity": 1 },
                 { "price_id": "pri_test_1", "quantity": 2 }]""";

        sendWebhook(completedEvent("txn_" + UUID.randomUUID(), String.valueOf(user.getId()), "\"12300\"", items), SECRET)
                .andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 12 + 2);
    }

    @Test
    void 설정에_없는_가격이_섞여_있으면_저장하지_않고_실패해서_재전송을_받는다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();
        String items = """
                [{ "price": { "id": "pri_test_5" }, "quantity": 1 },
                 { "price": { "id": "pri_unknown" }, "quantity": 1 }]""";

        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId()), "\"6150\"", items), SECRET)
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_PRICE"));

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS);
        assertThat(paymentRepository.existsByPaddleTransactionId(transactionId)).isFalse();
    }

    @Test
    void 금액이_객체로_와도_충전하고_금액은_0_으로_저장한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();

        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId()), "{ \"amount\": 4950 }", FIVE_CREDITS),
                SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
        assertThat(paymentOf(transactionId).getAmount()).isZero();
    }

    @Test
    void 결제_시각을_읽지_못해도_충전한다() throws Exception {
        User user = newUser();
        String body = completedEvent("txn_" + UUID.randomUUID(), String.valueOf(user.getId()))
                .replace("2026-10-31T00:59:58.000000Z", "not-a-date");

        sendWebhook(body, SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
    }

    @Test
    void customData_의_userId_가_객체면_사용자를_못_찾은_것으로_200() throws Exception {
        String transactionId = "txn_" + UUID.randomUUID();

        sendWebhook(completedEvent(transactionId, "{ \"id\": 1 }"), SECRET).andExpect(status().isOk());

        assertThat(paymentRepository.existsByPaddleTransactionId(transactionId)).isFalse();
    }

    @Test
    void 금액을_읽지_못해도_크레딧은_충전하고_금액은_0_으로_저장한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();

        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId()), "\"19.99\"", FIVE_CREDITS), SECRET)
                .andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
        assertThat(paymentOf(transactionId).getAmount()).isZero();
    }

    private Payment paymentOf(String transactionId) {
        return paymentsOf(transactionId).findFirst().orElseThrow();
    }

    private long paymentCount(String transactionId) {
        return paymentsOf(transactionId).count();
    }

    private Stream<Payment> paymentsOf(String transactionId) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getPaddleTransactionId().equals(transactionId));
    }

    @Test
    void 같은_거래의_웹훅이_두_번_와도_한_번만_저장하고_충전한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();
        String body = completedEvent(transactionId, String.valueOf(user.getId()));

        sendWebhook(body, SECRET).andExpect(status().isOk());
        sendWebhook(body, SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
        assertThat(paymentCount(transactionId)).isEqualTo(1);
    }

    @Test
    void 같은_거래의_웹훅이_동시에_와도_둘_다_200_이고_한_번만_충전한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();
        String body = completedEvent(transactionId, String.valueOf(user.getId()));

        // 두 스레드가 같이 출발하도록 맞춥니다. 매번 둘 다 중복 검사를 통과한다는 보장은 없지만, 통과해도 한 번만 충전돼야 합니다.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                responses.add(pool.submit(() -> {
                    start.await();
                    return sendWebhook(body, SECRET).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> response : responses) {
                assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(paymentCount(transactionId)).isEqualTo(1);
        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
    }

    @Test
    void customData_의_userId_가_문자열이어도_처리한다() throws Exception {
        User user = newUser();

        sendWebhook(completedEvent("txn_" + UUID.randomUUID(), "\"" + user.getId() + "\""), SECRET)
                .andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
    }

    @Test
    void 서명이_틀리면_401_이고_충전하지_않는다() throws Exception {
        User user = newUser();

        sendWebhook(completedEvent("txn_" + UUID.randomUUID(), String.valueOf(user.getId())), "forged-secret")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_WEBHOOK_SIGNATURE"));

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS);
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

    @Test
    void 거래_ID_가_비어_있으면_저장도_충전도_하지_않는다() throws Exception {
        User user = newUser();

        sendWebhook(completedEvent("", String.valueOf(user.getId())), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS);
        assertThat(paymentRepository.existsByPaddleTransactionId("")).isFalse();
    }

    // ── 환불 (#104) ──

    private static String adjustmentEvent(String eventType, String adjustmentId, String transactionId, String action,
                                          String status, String type, String total) {
        return """
                {
                  "event_id": "evt_%s",
                  "event_type": "%s",
                  "data": {
                    "id": "%s",
                    "action": "%s",
                    "type": "%s",
                    "status": "%s",
                    "transaction_id": "%s",
                    "currency_code": "KRW",
                    "totals": { "total": "%s" }
                  }
                }
                """.formatted(UUID.randomUUID(), eventType, adjustmentId, action, type, status, transactionId, total);
    }

    private static String approvedRefund(String transactionId, String type, String total) {
        return adjustmentEvent("adjustment.updated", "adj_" + UUID.randomUUID(), transactionId, "refund", "approved",
                type, total);
    }

    /** 5회권(4,950원)을 산 사용자. 크레딧은 무료 1 + 5 = 6 */
    private String paidFiveCredits(User user) throws Exception {
        String transactionId = "txn_" + UUID.randomUUID();
        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId())), SECRET).andExpect(status().isOk());
        return transactionId;
    }

    private Integer refundedCredits(String transactionId) {
        return jdbc.queryForObject("""
                select coalesce(sum(r.credits), 0) from payment_refund r join payment p on p.payment_id = r.payment_id
                where p.paddle_transaction_id = ?""", Integer.class, transactionId);
    }

    @Test
    void 전액_환불이_승인되면_그_결제로_충전한_크레딧을_모두_회수한다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);

        sendWebhook(approvedRefund(transactionId, "full", "4950"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS);
        assertThat(refundedCredits(transactionId)).isEqualTo(5);
    }

    @Test
    void 부분_환불은_환불_금액을_1회_가격으로_나눈_만큼_회수한다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);

        // 2,970 ÷ 990(4,950 ÷ 5) = 3회
        sendWebhook(approvedRefund(transactionId, "partial", "2970"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 2);
    }

    @Test
    void 환불_금액이_횟수로_나누어떨어지지_않으면_반올림해서_회수한다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);

        // 2,475 ÷ 990 = 2.5회 → 3회, 100 ÷ 990 = 0.1회 → 0회(소액 보상 환불)
        sendWebhook(approvedRefund(transactionId, "partial", "2475"), SECRET).andExpect(status().isOk());
        sendWebhook(approvedRefund(transactionId, "partial", "100"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 2);
    }

    @Test
    void 가격이_횟수로_나누어떨어지지_않아도_환불_계산_SQL_금액대로_환불하면_남은_횟수를_모두_회수한다() throws Exception {
        User user = newUser();
        String transactionId = "txn_" + UUID.randomUUID();
        String twelve = """
                [{ "price": { "id": "pri_test_12" }, "quantity": 1 }]""";
        sendWebhook(completedEvent(transactionId, String.valueOf(user.getId()), "\"999\"", twelve), SECRET)
                .andExpect(status().isOk());
        // 무료 1 + 12 중 8회 사용 → 12회권에서 7회 사용, 남은 5회
        jdbc.update("update users set credits = 5 where user_id = ?", user.getId());

        // SQL 은 5 × 999 DIV 12 = 416
        assertThat(refundAmounts(user)).containsExactly(416);
        sendWebhook(approvedRefund(transactionId, "partial", "416"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isZero();
        assertThat(refundedCredits(transactionId)).isEqualTo(5);
    }

    @Test
    void 승인_전에는_회수하지_않고_승인되면_한_번만_회수한다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);
        String adjustmentId = "adj_" + UUID.randomUUID();

        sendWebhook(adjustmentEvent("adjustment.created", adjustmentId, transactionId, "refund", "pending_approval",
                "full", "4950"), SECRET).andExpect(status().isOk());
        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);

        String approved = adjustmentEvent("adjustment.updated", adjustmentId, transactionId, "refund", "approved",
                "full", "4950");
        sendWebhook(approved, SECRET).andExpect(status().isOk());
        sendWebhook(approved, SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS);
        assertThat(refundedCredits(transactionId)).isEqualTo(5);
    }

    @Test
    void 거절된_환불은_회수하지_않는다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);

        sendWebhook(adjustmentEvent("adjustment.updated", "adj_" + UUID.randomUUID(), transactionId, "refund",
                "rejected", "full", "4950"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
        assertThat(refundedCredits(transactionId)).isZero();
    }

    @Test
    void 차지백도_환불처럼_회수한다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);

        sendWebhook(adjustmentEvent("adjustment.created", "adj_" + UUID.randomUUID(), transactionId, "chargeback",
                "approved", "full", "4950"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS);
    }

    @Test
    void 남은_크레딧보다_많이_회수해야_하면_0_에서_멈춘다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);
        jdbc.update("update users set credits = 2 where user_id = ?", user.getId());

        sendWebhook(approvedRefund(transactionId, "full", "4950"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isZero();
    }

    @Test
    void 나눠서_환불해도_충전한_횟수보다_많이_회수하지_않는다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);

        // 2,970원(3회)을 두 번 환불해도 두 번째는 남은 2회까지만 (Paddle 은 결제액보다 많이 환불하지 못하게 하지만 막아 둡니다)
        sendWebhook(approvedRefund(transactionId, "partial", "2970"), SECRET).andExpect(status().isOk());
        sendWebhook(approvedRefund(transactionId, "partial", "2970"), SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS);
        assertThat(refundedCredits(transactionId)).isEqualTo(5);
    }

    @Test
    void 차지백이_취소되면_실제로_뺀_크레딧만큼_돌려주라는_ERROR_를_남긴다(CapturedOutput output) throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);
        // 남은 2회뿐인데 5회를 회수해야 하는 차지백 → 실제로는 2회만 빠집니다.
        jdbc.update("update users set credits = 2 where user_id = ?", user.getId());
        String chargebackId = "adj_" + UUID.randomUUID();
        sendWebhook(adjustmentEvent("adjustment.created", chargebackId, transactionId, "chargeback", "approved",
                "full", "4950"), SECRET).andExpect(status().isOk());

        // 원래 차지백이 reversed 로 바뀌는 건 알리지 않습니다.
        sendWebhook(adjustmentEvent("adjustment.updated", chargebackId, transactionId, "chargeback", "reversed",
                "full", "4950"), SECRET).andExpect(status().isOk());
        assertThat(output.getOut()).doesNotContain("차지백이 취소돼 돈이 다시 들어왔습니다");

        sendWebhook(adjustmentEvent("adjustment.created", "adj_" + UUID.randomUUID(), transactionId,
                "chargeback_reverse", "approved", "full", "4950"), SECRET).andExpect(status().isOk());

        assertThat(output.getOut()).contains("크레딧 2회를 수동으로 돌려주고");
    }

    @Test
    void 뺀_크레딧이_없는_거래의_차지백_취소는_알리지_않는다(CapturedOutput output) throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);

        sendWebhook(adjustmentEvent("adjustment.created", "adj_" + UUID.randomUUID(), transactionId,
                "chargeback_reverse", "approved", "full", "4950"), SECRET).andExpect(status().isOk());

        assertThat(output.getOut()).doesNotContain("차지백이 취소돼 돈이 다시 들어왔습니다");
    }

    @Test
    void 환불_금액을_못_읽어도_회수_0_회로_기록해_한_번만_처리한다() throws Exception {
        User user = newUser();
        String transactionId = paidFiveCredits(user);
        String refund = adjustmentEvent("adjustment.updated", "adj_" + UUID.randomUUID(), transactionId, "refund",
                "approved", "partial", "19.99");

        sendWebhook(refund, SECRET).andExpect(status().isOk());
        sendWebhook(refund, SECRET).andExpect(status().isOk());

        assertThat(credits(user.getId())).isEqualTo(User.SIGNUP_FREE_CREDITS + 5);
        assertThat(jdbc.queryForObject("""
                select count(*) from payment_refund r join payment p on p.payment_id = r.payment_id
                where p.paddle_transaction_id = ? and r.credits = 0""", Integer.class, transactionId)).isEqualTo(1);
    }

    @Test
    void 저장된_결제가_없는_환불은_200_으로_넘긴다() throws Exception {
        sendWebhook(approvedRefund("txn_" + UUID.randomUUID(), "full", "4950"), SECRET).andExpect(status().isOk());
    }

    @Test
    void 환불_계산_SQL_은_무료와_먼저_산_결제부터_쓴_것으로_보고_환불_금액을_낸다() throws Exception {
        User user = newUser();
        String oneCredit = """
                [{ "price": { "id": "pri_test_1" }, "quantity": 1 }]""";
        List<String> singles = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String transactionId = "txn_" + UUID.randomUUID();
            sendWebhook(completedEvent(transactionId, String.valueOf(user.getId()), "\"1200\"", oneCredit), SECRET)
                    .andExpect(status().isOk());
            singles.add(transactionId);
        }
        String five = paidFiveCredits(user);
        // 무료 1 + 1회권 3 + 5회권 5 = 9 중 2회 사용 → 무료 1, 1회권 ① 1 을 쓴 것으로 봅니다.
        jdbc.update("update users set credits = 7 where user_id = ?", user.getId());

        assertThat(refundAmounts(user)).containsExactly(0, 1200, 1200, 4950);

        // 그대로 대시보드에서 환불하면 남은 7회가 모두 회수되고, 다시 계산하면 돌려줄 게 없습니다.
        sendWebhook(approvedRefund(singles.get(1), "full", "1200"), SECRET).andExpect(status().isOk());
        sendWebhook(approvedRefund(singles.get(2), "full", "1200"), SECRET).andExpect(status().isOk());
        sendWebhook(approvedRefund(five, "full", "4950"), SECRET).andExpect(status().isOk());
        assertThat(credits(user.getId())).isZero();
        assertThat(refundAmounts(user)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void 환불_계산_SQL_은_탈퇴한_사용자면_남은_크레딧을_0_으로_보고_계산한다() throws Exception {
        User user = newUser();
        paidFiveCredits(user);
        jdbc.update("delete from users where user_id = ?", user.getId());

        assertThat(refundAmounts(user)).containsExactly(0);
    }

    @Test
    void 환불_계산_SQL_은_금액을_못_읽은_결제면_NULL_을_내서_직접_계산하게_한다() throws Exception {
        User user = newUser();
        sendWebhook(completedEvent("txn_" + UUID.randomUUID(), String.valueOf(user.getId()), "\"19.99\"", FIVE_CREDITS),
                SECRET).andExpect(status().isOk());

        assertThat(refundAmounts(user)).containsExactly((Integer) null);
    }

    /** docs/refund-calculation.sql 을 그대로 실행합니다. @user_id 는 세션 변수라 같은 커넥션에서 SET 합니다. */
    private List<Integer> refundAmounts(User user) throws Exception {
        String sql = Files.readString(Path.of("docs/refund-calculation.sql"))
                .replaceAll("(?m)^SET @user_id = .*$", "");
        return jdbc.execute((ConnectionCallback<List<Integer>>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET @user_id = " + user.getId());
                List<Integer> amounts = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery(sql)) {
                    while (rows.next()) {
                        // getInt 는 NULL 을 0 으로 읽어서 NULL 이 나와도 통과해 버립니다.
                        amounts.add(rows.getObject("refund_amount", Integer.class));
                    }
                }
                return amounts;
            }
        });
    }
}
