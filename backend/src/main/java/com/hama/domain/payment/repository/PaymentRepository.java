package com.hama.domain.payment.repository;

import com.hama.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByPaddleTransactionId(String paddleTransactionId);

    /**
     * 거래 ID 를 먼저 차지합니다. 이미 있으면 예외 없이 0 행입니다(동시에 온 같은 거래는 앞의 커밋을 기다렸다가 0).
     * save 로 넣으면 unique 위반 예외가 나고 Hibernate 가 그 전에 WARN 을 남깁니다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO payment (user_id, paddle_transaction_id, amount, currency, credits, paid_at, created_at, updated_at)
            VALUES (:userId, :transactionId, :amount, :currency, :credits, :paidAt, :now, :now)""", nativeQuery = true)
    int insertIfAbsent(Long userId, String transactionId, int amount, String currency, int credits,
                       LocalDateTime paidAt, LocalDateTime now);

    /** 환불용. 같은 결제의 환불 웹훅이 동시에 와도 한 줄로 세웁니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paddleTransactionId = :paddleTransactionId")
    Optional<Payment> findByPaddleTransactionIdForUpdate(String paddleTransactionId);
}
