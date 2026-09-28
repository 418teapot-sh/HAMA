package com.hama.domain.auth.service;

import com.hama.domain.auth.entity.RefreshToken;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 INSERT 를 별도 트랜잭션(REQUIRES_NEW)으로 분리합니다.
 *
 * <p>같은 트랜잭션 안에서 {@code saveAndFlush} 가 unique 제약 위반으로 실패하면 Hibernate 세션이
 * 오염되어, 이후 같은 세션에서의 재조회가 {@code AssertionFailure} 로 죽습니다. INSERT 를 별도
 * 트랜잭션으로 떼어두면 실패해도 그쪽만 롤백되고 호출부는 재조회로 복구할 수 있습니다.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenWriter {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Long userId, String tokenHash) {
        refreshTokenRepository.saveAndFlush(RefreshToken.create(userId, tokenHash));
    }
}
