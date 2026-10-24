package com.hama.domain.payment.service;

import com.hama.domain.payment.entity.Payment;
import com.hama.domain.payment.entity.PaymentRefund;
import com.hama.domain.payment.exception.PaymentErrorCode;
import com.hama.domain.payment.repository.PaymentRefundRepository;
import com.hama.domain.payment.repository.PaymentRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
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
 *
 * 운영자: Paddle 대시보드에서 환불 (금액은 backend/docs/refund-calculation.sql 로 계산)
 *   → Paddle 승인 → adjustment.updated (status: approved)
 *   → 여기서 환불 저장 + 환불 금액만큼 크레딧 회수
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String TRANSACTION_COMPLETED = "transaction.completed";
    private static final String ADJUSTMENT_CREATED = "adjustment.created";
    private static final String ADJUSTMENT_UPDATED = "adjustment.updated";
    /** 돈이 사용자에게 돌아가는 조정. credit(인보이스 감액)은 돈이 오가지 않아 넘깁니다. */
    private static final Set<String> CLAWBACK_ACTIONS = Set.of("refund", "chargeback");
    private static final String CHARGEBACK_REVERSE = "chargeback_reverse";

    private final PaddleSignatureVerifier signatureVerifier;
    private final PaddleProperties properties;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
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
        JsonNode data = event.path("data");
        String id = data.path("id").asString();
        boolean handled = TRANSACTION_COMPLETED.equals(eventType) || ADJUSTMENT_CREATED.equals(eventType)
                || ADJUSTMENT_UPDATED.equals(eventType);
        if (handled && id.isBlank()) {
            // ID 가 없으면 중복 검사를 할 수 없습니다. 빈 ID 로 저장하면 다음 결제가 중복으로 걸립니다.
            log.error("[Payment] ID 가 없는 웹훅이라 처리하지 않았습니다. Paddle 대시보드에서 확인하세요 - type: {}, event: {}",
                    eventType, event.path("event_id").asString());
            return;
        }
        switch (eventType) {
            case TRANSACTION_COMPLETED -> transactionTemplate.executeWithoutResult(tx -> completeTransaction(data, id, tx));
            case ADJUSTMENT_CREATED, ADJUSTMENT_UPDATED -> transactionTemplate.executeWithoutResult(tx -> refund(data, id));
            // 대시보드에서 다른 이벤트까지 구독해 두어도 200 으로 받고 넘깁니다. 에러를 내면 Paddle 이 계속 재전송합니다.
            default -> log.info("[Payment] 처리하지 않는 웹훅 - type: {}, event: {}", eventType,
                    event.path("event_id").asString());
        }
    }

    /**
     * Paddle 은 같은 웹훅을 여러 번 보낼 수 있어서(응답 지연·재시도) 거래 ID 로 한 번만 처리합니다.
     * 실제로 막는 건 결제 행을 먼저 넣는 insertIfAbsent 입니다. 앞의 검사는 이미 처리한 거래의 재전송을 가격 확인 전에 거르려고 둡니다.
     * 없으면 가격 설정이 바뀐 뒤 옛 거래가 재전송될 때 UNKNOWN_PRICE 로 실패하고, 금액 ERROR 로그도 재전송마다 다시 찍힙니다.
     * 동시에 온 같은 거래는 거기서 0 행이 되어 사용자 행을 건드리기 전에 끝납니다.
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

    /**
     * 승인된 환불·차지백이면 그 결제로 충전한 크레딧 중 환불 금액만큼을 회수합니다.
     * 회수할 횟수 = 환불 금액 ÷ 1회 가격(결제 금액 ÷ 충전 횟수), 반올림합니다.
     * refund-calculation.sql 이 금액을 내림해서 내므로(12회권 999 에서 5회 → 416 = 4.997회) 버리면 1회가 덜 회수되고,
     * 올리면 소액 보상 환불(0.1회어치)에도 1회가 빠집니다.
     * 예: 5회권 4,950원에서 2,970원 환불 → 2,970 ÷ 990 = 3회.
     *
     * <p>결제 행을 가장 먼저 잠급니다(FOR UPDATE). 같은 결제의 환불 두 건이 동시에 와도 한 줄로 서서, 뒤의 것이
     * 앞의 회수 기록을 보고 남은 만큼만 뺍니다. 기본 격리 수준(REPEATABLE READ)은 첫 일반 조회 시점의 스냅샷을 쓰므로
     * 잠금보다 먼저 일반 조회를 하면 안 됩니다. 락 순서는 결제 → 사용자입니다.
     */
    private void refund(JsonNode adjustment, String adjustmentId) {
        String action = adjustment.path("action").asString();
        String status = adjustment.path("status").asString();
        String transactionId = adjustment.path("transaction_id").asString();
        if (!"approved".equals(status)) {
            // 환불은 pending_approval 로 만들어진 뒤 Paddle 이 승인하면 adjustment.updated 로 다시 옵니다.
            log.info("[Payment] 승인되지 않은 조정이라 넘깁니다 - adj: {}, action: {}, status: {}", adjustmentId, action, status);
            return;
        }
        if (CHARGEBACK_REVERSE.equals(action)) {
            alertChargebackReversed(adjustmentId, transactionId);
            return;
        }
        if (!CLAWBACK_ACTIONS.contains(action)) {
            log.info("[Payment] 처리하지 않는 조정 - adj: {}, action: {}", adjustmentId, action);
            return;
        }
        Payment payment = paymentRepository.findByPaddleTransactionIdForUpdate(transactionId).orElse(null);
        if (payment == null) {
            // 사용자를 못 찾아 충전하지 않은 결제(completeTransaction 의 ERROR 로그)라 회수할 크레딧이 없습니다.
            log.warn("[Payment] 저장된 결제가 없는 환불이라 회수하지 않습니다 - adj: {}, txn: {}", adjustmentId, transactionId);
            return;
        }
        // 이 결제의 환불 기록 한 번 조회로 중복 검사와 지금까지 회수한 횟수를 같이 봅니다.
        List<PaymentRefund> refunds = refundRepository.findByPaymentId(payment.getId());
        if (refunds.stream().anyMatch(refund -> refund.getPaddleAdjustmentId().equals(adjustmentId))) {
            log.info("[Payment] 이미 처리한 환불 - adj: {}", adjustmentId);
            return;
        }

        JsonNode total = adjustment.path("totals").path("total");
        OptionalInt amount = readInt(total);
        int credits;
        if ("full".equals(adjustment.path("type").asString())) {
            credits = payment.getCredits();
        } else if (amount.isPresent() && amount.getAsInt() > 0 && payment.getAmount() > 0) {
            // floor(x + 0.5) 를 정수로: (2 × 금액 × 횟수 + 결제 금액) ÷ (2 × 결제 금액)
            credits = (int) ((2L * amount.getAsInt() * payment.getCredits() + payment.getAmount()) / (2L * payment.getAmount()));
        } else {
            // 기록이 없으면 환불 계산 SQL 이 이 결제를 아직 환불 안 한 것으로 보고, 재전송마다 이 로그가 반복됩니다.
            refundRepository.save(PaymentRefund.create(payment.getId(), adjustmentId, action, amount.orElse(0), 0, 0));
            log.error("[Payment] 환불 금액이나 결제 금액을 읽지 못해 회수하지 않았습니다. 수동으로 회수하고 이 adj 의 payment_refund.credits 를 회수한 횟수로 고치세요 - adj: {}, txn: {}, total: {}",
                    adjustmentId, transactionId, total);
            return;
        }
        // 나눠서 여러 번 환불하면 합이 충전한 횟수를 넘을 수 있어(운영자 입력 실수 등) 남은 만큼으로 자릅니다.
        credits = Math.max(Math.min(credits, payment.getCredits() - refunds.stream().mapToInt(PaymentRefund::getCredits).sum()), 0);

        Long userId = payment.getUserId();
        Integer balance = userRepository.findByIdForUpdate(userId).map(User::getCredits).orElse(null);
        int taken = balance == null ? 0 : Math.min(credits, balance);
        refundRepository.save(PaymentRefund.create(payment.getId(), adjustmentId, action, amount.orElse(0), credits, taken));
        if (balance == null) {
            log.info("[Payment] 탈퇴한 사용자의 환불이라 기록만 남깁니다 - adj: {}, txn: {}, userId: {}", adjustmentId, transactionId, userId);
            return;
        }
        userRepository.takeBackCredits(userId, credits);
        if (balance < credits) {
            // 환불 처리 중에 크레딧을 더 썼거나 환불 금액을 많이 넣은 경우입니다. 약관상 처리 시점 기준이라 0 에서 멈춥니다.
            log.warn("[Payment] 남은 크레딧이 회수할 횟수보다 적어 0 으로 맞췄습니다 - adj: {}, userId: {}, 회수: {}, 남은 크레딧: {}",
                    adjustmentId, userId, credits, balance);
        }
        log.info("[Payment] 환불 - adj: {}, txn: {}, userId: {}, action: {}, amount: {} {}, credits: -{}",
                adjustmentId, transactionId, userId, action, amount.orElse(0), payment.getCurrency(), taken);
    }

    /**
     * Paddle 이 차지백 분쟁에서 이기면 chargeback_reverse 조정을 새로 만듭니다(원래 차지백이 reversed 로 바뀌는 건 그 결과라 따로 보지 않음).
     * 돈이 다시 들어왔으니 차지백 때 실제로 뺀 크레딧을 돌려줘야 합니다. 드문 일이라 자동으로 하지 않고 사람이 처리합니다.
     */
    private void alertChargebackReversed(String adjustmentId, String transactionId) {
        int taken = refundRepository.sumTakenChargebackCredits(transactionId);
        if (taken == 0) {
            log.info("[Payment] 차지백이 취소됐지만 뺀 크레딧이 없어 넘깁니다 - adj: {}, txn: {}", adjustmentId, transactionId);
            return;
        }
        log.error("[Payment] 차지백이 취소돼 돈이 다시 들어왔습니다. 크레딧 {}회를 수동으로 돌려주고 그 거래의 chargeback payment_refund 행을 지우세요 - adj: {}, txn: {}",
                taken, adjustmentId, transactionId);
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
     * 못 읽었다고 예외를 내면 롤백돼서 돈을 낸 사용자가 크레딧을 못 받고, Paddle 재전송도 매번 같은 이유로 실패하므로
     * 0 으로 저장하고 로그만 남깁니다. 0 으로 저장된 결제는 부분 환불 때 회수할 횟수를 계산할 수 없어 수동으로 회수해야 합니다.
     */
    private static int parseAmount(JsonNode transaction, String transactionId) {
        JsonNode total = transaction.path("details").path("totals").path("total");
        OptionalInt amount = readInt(total);
        if (amount.isEmpty()) {
            log.error("[Payment] 결제 금액을 읽지 못해 0 으로 저장합니다. Paddle 대시보드에서 확인하세요 - txn: {}, total: {}",
                    transactionId, total);
        }
        return amount.orElse(0);
    }

    /** Paddle 금액은 최소 단위 정수 문자열("4950")입니다. 객체·소수 등이면 빈 값입니다. */
    private static OptionalInt readInt(JsonNode node) {
        try {
            return OptionalInt.of(Integer.parseInt(node.isValueNode() ? node.asString() : node.toString()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
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
