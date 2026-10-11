package com.hama.domain.payment.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 완료된 결제 한 건입니다. 영수증·세금·환불 원장은 Paddle(판매 대행사)이 갖고 있고,
 * 여기에는 "누가 언제 얼마를 내고 몇 회를 충전했나"만 남깁니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Paddle 거래 ID({@code txn_...}). 같은 웹훅이 여러 번 와도 한 번만 저장되도록 unique 입니다. */
    @Column(nullable = false, unique = true)
    private String paddleTransactionId;

    /** 결제 금액. {@link #currency} 의 최소 단위입니다(KRW 는 원, USD 는 센트). */
    @Column(nullable = false)
    private int amount;

    /**
     * ISO 4217 통화 코드(KRW, USD …). Paddle 은 해외 구매자에게 현지 통화로 결제시킬 수 있어서,
     * 이게 없으면 amount 200 이 200원인지 200센트인지 구분할 수 없습니다.
     */
    @Column(nullable = false, length = 3)
    private String currency;

    /** 이 결제로 충전한 크레딧. 환불할 때 회수할 횟수입니다. */
    @Column(nullable = false)
    private int credits;

    @Column(nullable = false)
    private LocalDateTime paidAt;

    public static Payment create(Long userId, String paddleTransactionId, int amount, String currency, int credits,
                                 LocalDateTime paidAt) {
        return Payment.builder()
                .userId(userId)
                .paddleTransactionId(paddleTransactionId)
                .amount(amount)
                .currency(currency)
                .credits(credits)
                .paidAt(paidAt)
                .build();
    }
}
