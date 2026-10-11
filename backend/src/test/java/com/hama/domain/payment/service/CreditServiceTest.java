package com.hama.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.payment.exception.PaymentErrorCode;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class CreditServiceTest {

    @Autowired
    private CreditService creditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transaction;

    private Long newUser() {
        return userRepository.save(User.create("credit-" + UUID.randomUUID() + "@hama.com", "encoded", "하마", false))
                .getId();
    }

    private int credits(Long userId) {
        return userRepository.findById(userId).orElseThrow().getCredits();
    }

    @Test
    void 쓰면_1회_줄고_없으면_402() {
        Long userId = newUser();

        transaction.executeWithoutResult(status -> creditService.use(userId));
        assertThat(credits(userId)).isZero();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> creditService.use(userId)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.INSUFFICIENT_CREDITS));
        assertThat(credits(userId)).isZero();
    }

    @Test
    void 호출한_쪽이_롤백되면_차감도_취소된다() {
        Long userId = newUser();

        transaction.executeWithoutResult(status -> {
            creditService.use(userId);
            status.setRollbackOnly();
        });

        assertThat(credits(userId)).isEqualTo(User.SIGNUP_FREE_CREDITS);
    }

    @Test
    void 돌려주면_1회_는다() {
        Long userId = newUser();

        transaction.executeWithoutResult(status -> {
            creditService.use(userId);
            creditService.giveBack(userId);
        });

        assertThat(credits(userId)).isEqualTo(User.SIGNUP_FREE_CREDITS);
    }

    @Test
    void 트랜잭션_밖에서는_부를_수_없다() {
        Long userId = newUser();

        assertThatThrownBy(() -> creditService.use(userId)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
