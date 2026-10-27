package com.hama.domain.payment.repository;

import com.hama.domain.payment.entity.PaymentRefund;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    /** 차지백이 취소됐을 때 돌려줄 크레딧. 그 거래의 차지백으로 실제로 뺀 횟수 합계입니다. */
    @Query("""
            SELECT COALESCE(SUM(r.takenCredits), 0) FROM PaymentRefund r JOIN Payment p ON p.id = r.paymentId
            WHERE p.paddleTransactionId = :paddleTransactionId AND r.action = 'chargeback'""")
    int sumTakenChargebackCredits(String paddleTransactionId);

    /** 한 결제의 환불 기록. 여러 번 나눠 환불했을 때 지금까지 회수한 횟수를 셉니다. */
    List<PaymentRefund> findByPaymentId(Long paymentId);
}
