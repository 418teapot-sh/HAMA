package com.hama.domain.auth.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호 재설정 링크. 사용자당 한 행이고(user_id unique), 메일을 다시 보내면 같은 행의 해시를 바꿔서 옛 링크는 무효가 됩니다.
 * 저장·교체는 동시 요청에도 행이 하나로 유지되도록 PasswordResetTokenRepository.upsert 한 문장으로만 합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_token_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    /** 메일로 보낸 원본 토큰의 SHA-256 해시. 원본은 저장하지 않습니다. */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
