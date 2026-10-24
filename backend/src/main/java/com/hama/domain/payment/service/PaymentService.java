package com.hama.domain.payment.service;

import com.hama.domain.payment.exception.PaymentErrorCode;
import com.hama.domain.payment.repository.PaymentRepository;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Paddle 웹훅을 처리합니다. 결제 흐름:
 * <pre>
 * 프론트: Paddle.Checkout.open({ items, customData: { userId } })
 *   → 사용자 결제
 *   → Paddle 이 POST /api/v1/payments/webhook (transaction.completed)
 *   → 여기서 결제 저장 + 산 가격(items[].price.id)만큼 크레딧 충전
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String TRANSACTION_COMPLETED = "transaction.completed";

    private final PaddleSignatureVerifier signatureVerifier;
    private final PaddleProperties properties;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 토큰 없이 열린 경로라 서명부터 확인합니다. 트랜잭션(DB 연결)은 서명이 맞을 때만 엽니다.
     * 그렇지 않으면 위조 요청만으로 커넥션 풀을 다 쓸 수 있습니다.
     */
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
        JsonNode transaction = event.path("data");
        String transactionId = transaction.path("id").asString();
        if (transactionId.isBlank()) {
            // ID 가 없으면 중복 검사를 할 수 없습니다. 빈 ID 로 저장하면 다음 결제가 중복으로 걸립니다.
            log.error("[Payment] ID 가 없는 웹훅이라 처리하지 않았습니다. Paddle 대시보드에서 확인하세요 - type: {}, event: {}",
                    eventType, event.path("event_id").asString());
            return;
        }
        transactionTemplate.executeWithoutResult(tx -> completeTransaction(transaction, transactionId, tx));
    }

    /**
     * Paddle 은 같은 웹훅을 여러 번 보낼 수 있어서(응답 지연·재시도) 거래 ID 로 한 번만 처리합니다.
     * 실제로 막는 건 결제 행을 먼저 넣는 insertIfAbsent 입니다. 앞의 검사는 이미 처리한 거래의 재전송을 가격 확인 전에 거르려고 둡니다.
     * 없으면 가격 설정이 바뀐 뒤 옛 거래가 재전송될 때 UNKNOWN_PRICE 로 실패하고, 금액 ERROR 로그도 재전송마다 다시 찍힙니다.
     * 동시에 온 같은 거래는 insertIfAbsent 에서 0 행이 되어 사용자 행을 건드리기 전에 끝납니다.
     */
    private void completeTransaction(JsonNode transaction, String transactionId, TransactionStatus tx) {
        if (paymentRepository.existsByPaddleTransactionId(transactionId)) {
            log.info("[Payment] 이미 처리한 거래 - txn: {}", transactionId);
            return;
        }

        JsonNode customData = transaction.path("custom_data");
        Long userId = parseUserId(customData.path("userId")).orElse(null);
        if (userId == null) {
            logUnknownUser(transactionId, customData);
            return;
        }
        int credits = creditsOf(transaction.path("items"), transactionId);
        int amount = parseAmount(transaction, transactionId);
        String currency = transaction.path("currency_code").asString();
        if (paymentRepository.insertIfAbsent(userId, transactionId, amount, currency, credits,
                paidAt(transaction, transactionId), LocalDateTime.now()) == 0) {
            log.info("[Payment] 동시에 온 같은 거래라 넘깁니다 - txn: {}", transactionId);
            return;
        }
        // 충전 UPDATE 가 바꾼 행이 없으면 없는 사용자입니다. 따로 조회하면 그 사이에 탈퇴한 경우를 놓칩니다.
        if (userRepository.addCredits(userId, credits) == 0) {
            tx.setRollbackOnly(); // 넣은 결제 행을 지웁니다. 없는 사용자의 결제는 저장하지 않습니다.
            logUnknownUser(transactionId, customData);
            return;
        }
        log.info("[Payment] 결제 완료 - txn: {}, userId: {}, amount: {} {}, credits: +{}",
                transactionId, userId, amount, currency, credits);
    }

    /** 돈은 받았는데 누구 것인지 모르는 상태입니다. 재시도해도 결과가 같으니 200 으로 끝내고 사람이 처리합니다. */
    private static void logUnknownUser(String transactionId, JsonNode customData) {
        log.error("[Payment] 결제는 완료됐지만 사용자를 찾지 못했습니다. Paddle 대시보드에서 확인 후 수동 처리하세요 - txn: {}, customData: {}",
                transactionId, customData);
    }

    /**
     * 산 가격마다 설정(paddle.credit-price-ids)에서 횟수를 찾아 수량만큼 더합니다.
     * 모르는 가격이 하나라도 있으면(가격 ID 설정 누락, Sandbox·Live ID 혼동) 돈은 받았는데 줄 크레딧을 모르는 상태입니다.
     * 저장하면 중복 검사에 막혀 다시 처리할 수 없으니 저장하지 않고 실패로 응답합니다. Paddle 이 재전송하므로 설정을 고치면 그때 충전됩니다.
     */
    private int creditsOf(JsonNode items, String transactionId) {
        int total = 0;
        for (JsonNode item : items) {
            String priceId = item.path("price").path("id").asString(item.path("price_id").asString());
            int credits = properties.creditsOf(priceId);
            if (credits == 0) {
                log.error("[Payment] 설정에 없는 가격이라 처리하지 않았습니다. paddle.credit-price-ids 와 Paddle 카탈로그를 확인하세요 - txn: {}, price: {}",
                        transactionId, priceId);
                throw new BusinessException(PaymentErrorCode.UNKNOWN_PRICE);
            }
            total += credits * Math.max(item.path("quantity").asInt(1), 1);
        }
        if (total == 0) {
            log.error("[Payment] 결제에 상품이 없습니다 - txn: {}", transactionId);
            throw new BusinessException(PaymentErrorCode.UNKNOWN_PRICE);
        }
        return total;
    }

    /**
     * 금액은 기록용입니다. 못 읽었다고 예외를 내면 롤백돼서 돈을 낸 사용자가 크레딧을 못 받고,
     * Paddle 재전송도 매번 같은 이유로 실패하므로 0 으로 저장하고 로그만 남깁니다.
     */
    private static int parseAmount(JsonNode transaction, String transactionId) {
        JsonNode node = transaction.path("details").path("totals").path("total");
        String total = node.isValueNode() ? node.asString() : node.toString();
        try {
            return Integer.parseInt(total);
        } catch (NumberFormatException e) {
            log.error("[Payment] 결제 금액을 읽지 못해 0 으로 저장합니다. Paddle 대시보드에서 확인하세요 - txn: {}, total: {}",
                    transactionId, total);
            return 0;
        }
    }

    /** 프론트가 customData 에 숫자로 넣든 문자열로 넣든 받습니다. 객체·배열이면 없는 것으로 봅니다. */
    private static Optional<Long> parseUserId(JsonNode userId) {
        if (!userId.isValueNode()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(userId.asString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * billed_at 은 UTC(RFC 3339)로 옵니다. 다른 시각 컬럼처럼 서버 시간대로 바꿔 저장합니다.
     * 기록용이라 못 읽으면 금액처럼 실패시키지 않고 지금 시각으로 저장합니다(실패하면 재전송도 매번 같은 이유로 실패).
     */
    private static LocalDateTime paidAt(JsonNode transaction, String transactionId) {
        String billedAt = transaction.path("billed_at").asString("");
        try {
            return OffsetDateTime.parse(billedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            if (!billedAt.isEmpty()) {
                log.error("[Payment] 결제 시각을 읽지 못해 지금 시각으로 저장합니다 - txn: {}, billed_at: {}", transactionId, billedAt);
            }
            return LocalDateTime.now();
        }
    }
}
