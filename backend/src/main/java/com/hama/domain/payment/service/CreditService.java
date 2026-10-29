package com.hama.domain.payment.service;

import com.hama.domain.payment.exception.PaymentErrorCode;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 목표 확정·일정 재계산에서 크레딧을 씁니다. 충전은 {@link PaymentService} 가 합니다.
 *
 * <p>호출한 쪽 트랜잭션 안에서만 부를 수 있습니다(MANDATORY). 그래야 뒤에서 실패해 롤백되면 차감도 같이 취소됩니다.
 * 사용자 행을 잠그므로 목표·세션을 잠그기 전에 부르세요. 회원탈퇴(UserService.withdraw)의 락 순서가 사용자 → 세션 → 목표라서,
 * 순서가 거꾸로면 같은 사용자의 탈퇴와 서로 기다릴 수 있습니다.
 */
@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;

    /** 1회를 씁니다. 남은 크레딧이 없으면 402 INSUFFICIENT_CREDITS 입니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void use(Long userId) {
        if (userRepository.useCredit(userId) == 0) {
            throw new BusinessException(PaymentErrorCode.INSUFFICIENT_CREDITS);
        }
    }

    /**
     * 차감 없이 남은 크레딧이 있는지만 봅니다. 없으면 402 INSUFFICIENT_CREDITS 입니다.
     * 목표 생성처럼 나중에 크레딧을 쓸 흐름의 입구에서 막는 용도라 사용자 행을 잠그지 않습니다.
     */
    @Transactional(readOnly = true)
    public void requireAvailable(Long userId) {
        if (!userRepository.existsByIdAndCreditsGreaterThan(userId, 0)) {
            throw new BusinessException(PaymentErrorCode.INSUFFICIENT_CREDITS);
        }
    }

    /** 먼저 쓴 1회를 돌려줍니다. 실제로 한 일이 없을 때(재계산할 투두가 없음) 씁니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void giveBack(Long userId) {
        userRepository.addCredits(userId, 1);
    }
}
