package com.hama.domain.payment.service;

import com.hama.domain.payment.entity.Payment;
import com.hama.domain.payment.exception.PaymentErrorCode;
import com.hama.domain.payment.repository.PaymentRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Paddle 웹훅을 처리합니다. 결제 흐름:
 * <pre>
 * 프론트: Paddle.Checkout.open({ items, customData: { userId } })
 *   → 사용자 결제
 *   → Paddle 이 POST /api/payments/webhook (transaction.completed)
 *   → 여기서 결제 저장 + 프리미엄 켜기
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String TRANSACTION_COMPLETED = "transaction.completed";

    private final PaddleSignatureVerifier signatureVerifier;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public void handleWebhook(String signature, byte[] rawBody) {
        if (!signatureVerifier.isValid(signature, rawBody)) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        JsonNode event = jsonMapper.readTree(rawBody);
        String eventType = event.path("event_type").asString();
        // 대시보드에서 다른 이벤트까지 구독해 두어도 200 으로 받고 넘깁니다. 에러를 내면 Paddle 이 계속 재전송합니다.
        if (!TRANSACTION_COMPLETED.equals(eventType)) {
            log.info("[Payment] 처리하지 않는 웹훅 - type: {}, event: {}", eventType, event.path("event_id").asString());
            return;
        }
        completeTransaction(event.path("data"));
    }

    /**
     * Paddle 은 같은 웹훅을 여러 번 보낼 수 있어서(응답 지연·재시도) 거래 ID 로 한 번만 처리합니다.
     * 두 요청이 거의 동시에 오면 둘 다 아래 검사를 통과할 수 있지만, paddle_transaction_id unique 에 걸린 쪽은
     * 500 으로 끝나고 Paddle 이 다시 보냈을 때 이미 처리된 거래로 넘어갑니다.
     */
    private void completeTransaction(JsonNode transaction) {
        String transactionId = transaction.path("id").asString();
        if (paymentRepository.existsByPaddleTransactionId(transactionId)) {
            log.info("[Payment] 이미 처리한 거래 - txn: {}", transactionId);
            return;
        }

        JsonNode customData = transaction.path("custom_data");
        User user = parseUserId(customData.path("userId"))
                .flatMap(userRepository::findById)
                .orElse(null);
        if (user == null) {
            // 돈은 받았는데 누구 것인지 모르는 상태입니다. 재시도해도 결과가 같으니 200 으로 끝내고 사람이 처리합니다.
            log.error("[Payment] 결제는 완료됐지만 사용자를 찾지 못했습니다. Paddle 대시보드에서 확인 후 수동 처리하세요 - txn: {}, customData: {}",
                    transactionId, customData);
            return;
        }

        user.markPremium();
        int amount = parseAmount(transaction, transactionId);
        String currency = transaction.path("currency_code").asString();
        paymentRepository.save(Payment.create(user.getId(), transactionId, amount, currency, paidAt(transaction)));
        log.info("[Payment] 결제 완료 - txn: {}, userId: {}, amount: {} {}", transactionId, user.getId(), amount, currency);
    }

    /**
     * 금액은 기록용입니다. 못 읽었다고 예외를 내면 롤백돼서 돈을 낸 사용자가 프리미엄을 못 받고,
     * Paddle 재전송도 매번 같은 이유로 실패하므로 0 으로 저장하고 로그만 남깁니다.
     */
    private static int parseAmount(JsonNode transaction, String transactionId) {
        String total = transaction.path("details").path("totals").path("total").asString();
        try {
            return Integer.parseInt(total);
        } catch (NumberFormatException e) {
            log.error("[Payment] 결제 금액을 읽지 못해 0 으로 저장합니다. Paddle 대시보드에서 확인하세요 - txn: {}, total: {}",
                    transactionId, total);
            return 0;
        }
    }

    /** 프론트가 customData 에 숫자로 넣든 문자열로 넣든 받습니다. */
    private static Optional<Long> parseUserId(JsonNode userId) {
        try {
            return Optional.of(Long.valueOf(userId.asString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** billed_at 은 UTC(RFC 3339)로 옵니다. 다른 시각 컬럼처럼 서버 시간대로 바꿔 저장합니다. */
    private static LocalDateTime paidAt(JsonNode transaction) {
        String billedAt = transaction.path("billed_at").asString();
        if (billedAt.isEmpty()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(billedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
