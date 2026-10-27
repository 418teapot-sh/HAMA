package com.hama.domain.auth.repository;

import com.hama.domain.auth.entity.PasswordResetToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** RefreshTokenRepository 처럼 조회 후 수정 대신 한 문장으로 바꿔서, 같은 요청이 동시에 와도 행이 꼬이지 않게 합니다. */
@Transactional
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** 사용자의 링크를 새로 만들거나, 이미 있으면 해시와 만료 시각을 바꿔 옛 링크를 무효로 합니다. */
    @Modifying
    @Query(value = """
            INSERT INTO password_reset_token (user_id, token_hash, expires_at, created_at, updated_at)
            VALUES (:userId, :tokenHash, :expiresAt, :now, :now)
            ON DUPLICATE KEY UPDATE token_hash = VALUES(token_hash), expires_at = VALUES(expires_at), updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    void upsert(Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime now);

    Optional<PasswordResetToken> findByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    /** 링크를 쓴 것으로 처리합니다. 같은 링크로 동시에 요청하면 한쪽만 1, 나머지는 0 입니다. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    int deleteByTokenHash(String tokenHash);
}
