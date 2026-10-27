package com.hama.domain.payment.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 승인된 환불·차지백 한 건입니다. 어느 결제에서 얼마를 돌려주고 몇 회를 회수했나만 남깁니다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentRefund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_id")
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    /** Paddle 조정 ID({@code adj_...}). 같은 웹훅이 여러 번 와도 한 번만 회수되도록 unique 입니다. */
    @Column(nullable = false, unique = true)
    private String paddleAdjustmentId;

    /** refund 또는 chargeback */
    @Column(nullable = false, length = 20)
    private String action;

    /** 환불 금액. 결제의 currency 최소 단위입니다. */
    @Column(nullable = false)
    private int amount;

    /** 이 환불로 회수할 크레딧. 남은 크레딧이 모자라 덜 뺐어도 계산한 횟수를 남깁니다(환불 계산 SQL 용). */
    @Column(nullable = false)
    private int credits;

    /** 실제로 뺀 크레딧. 차지백이 취소되면 이만큼 돌려줍니다. */
    @Column(nullable = false)
    private int takenCredits;

    public static PaymentRefund create(Long paymentId, String paddleAdjustmentId, String action, int amount,
                                       int credits, int takenCredits) {
        return PaymentRefund.builder()
                .paymentId(paymentId)
                .paddleAdjustmentId(paddleAdjustmentId)
                .action(action)
                .amount(amount)
                .credits(credits)
                .takenCredits(takenCredits)
                .build();
    }
}
