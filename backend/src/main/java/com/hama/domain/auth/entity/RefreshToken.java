package com.hama.domain.auth.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자당 리프레시 토큰 하나만 저장합니다(user_id unique).
 * 다른 기기에서 로그인하면 이전 기기의 리프레시 토큰은 무효가 됩니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    /** 원본 토큰이 아니라 RefreshTokenHasher 로 해시한 값. DB 가 유출돼도 토큰을 그대로 쓸 수 없게 합니다. */
    @Column(nullable = false)
    private String tokenHash;

    public static RefreshToken create(Long userId, String tokenHash) {
        return RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .build();
    }

    /** 재발급(rotation). 이전 토큰은 해시가 달라져 더 이상 통과하지 못합니다. */
    public void rotate(String newTokenHash) {
        this.tokenHash = newTokenHash;
    }
}
