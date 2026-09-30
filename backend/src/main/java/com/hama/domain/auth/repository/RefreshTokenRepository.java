package com.hama.domain.auth.repository;

import com.hama.domain.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재발급·로그아웃은 조회 후 수정이 아니라 한 문장(UPDATE/DELETE ... WHERE)으로 처리합니다.
 * 같은 토큰으로 요청이 동시에 와도 DB 가 행 잠금으로 줄을 세워서, 뒤의 요청은 조건이 안 맞아 0 행이 됩니다.
 * 시각은 JPA Auditing 과 같은 JVM 시계를 쓰도록 호출부가 넘깁니다.
 *
 * <p>{@code @Transactional}: 서비스 트랜잭션 안에서 부르면 거기에 합류하고, 밖에서 부르면
 * (만료 행 정리처럼) 호출마다 따로 커밋합니다.
 */
@Transactional
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 저장된 해시가 {@code oldHash} 인 행만 교체하고, 바뀐 행 수(0 또는 1)를 돌려줍니다. */
    @Modifying
    @Query("""
            UPDATE RefreshToken r SET r.tokenHash = :newHash, r.updatedAt = :now
            WHERE r.userId = :userId AND r.tokenHash = :oldHash
            """)
    int rotate(Long userId, String oldHash, String newHash, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId AND r.tokenHash = :tokenHash")
    void deleteByUserIdAndTokenHash(Long userId, String tokenHash);

    /**
     * 마지막 발급·재발급이 {@code cutoff} 이전인 행, 즉 이미 만료된 토큰의 행을 최대 1000개 지우고 지운 수를 돌려줍니다.
     * 한 번에 다 지우면 그 트랜잭션이 끝날 때까지 넓은 범위에 락이 걸려 로그인·재발급이 기다리므로,
     * 호출부가 0 이 나올 때까지 반복하고 묶음마다 따로 커밋합니다.
     */
    @Modifying
    @Query(value = "DELETE FROM refresh_token WHERE updated_at < :cutoff LIMIT 1000", nativeQuery = true)
    int deleteExpiredBatch(LocalDateTime cutoff);
}
