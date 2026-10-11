package com.hama.domain.payment.repository;

import com.hama.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByPaddleTransactionId(String paddleTransactionId);
}
