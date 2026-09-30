package com.hama.domain.auth.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인한 기기(세션)마다 한 행입니다. 한 유저가 여러 기기에서 동시에 로그인할 수 있고,
 * 로그아웃하면 그 기기의 행만 지웁니다.
 */
@Entity
// 만료된 행을 지우는 정리 작업(AuthService.deleteExpiredRefreshTokens)용입니다.
@Table(indexes = @Index(columnList = "updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /**
     * 원본 토큰이 아니라 RefreshTokenHasher 로 해시한 값. DB 가 유출돼도 토큰을 그대로 쓸 수 없게 합니다.
     * 재발급·로그아웃은 이 값으로 행을 찾습니다.
     */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    public static RefreshToken create(Long userId, String tokenHash) {
        return RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .build();
    }
}
