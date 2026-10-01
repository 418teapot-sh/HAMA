package com.hama.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * 토큰 생성·해석 규칙을 고정합니다.
 *
 * <p>parseXxx 가 예외를 던지면 JwtAuthenticationFilter 안에서 터지는데, 필터는
 * {@code @RestControllerAdvice} 바깥이라 401 이어야 할 상황이 500 HTML 로 나갑니다.
 * 그래서 잘못된 입력에는 예외가 아니라 빈 Optional 이 나와야 합니다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-only-secret-key-do-not-use-in-production-0123456789";
    private static final Long USER_ID = 42L;

    private final JwtTokenProvider provider = new JwtTokenProvider(
            new JwtProperties(SECRET, 30 * 60 * 1000L, 14L * 24 * 60 * 60 * 1000));

    @Test
    void 액세스_토큰으로_인증정보를_만든다() {
        String access = provider.createAccessToken(USER_ID);

        assertThat(provider.parseAccessUser(access)).contains(new AuthUser(USER_ID));
    }

    @Test
    void 리프레시_토큰으로는_인증되지_않는다() {
        // 서명은 멀쩡하므로 type 을 안 보면 그대로 통과해서, 14일짜리 토큰이 액세스 토큰 노릇을 합니다.
        String refresh = provider.createRefreshToken(USER_ID);

        assertThat(provider.parseAccessUser(refresh)).isEmpty();
        assertThat(provider.parseRefreshUserId(refresh)).contains(USER_ID);
    }

    @Test
    void 액세스_토큰으로는_재발급되지_않는다() {
        String access = provider.createAccessToken(USER_ID);

        assertThat(provider.parseRefreshUserId(access)).isEmpty();
    }

    @Test
    void 같은_순간에_발급한_리프레시_토큰도_서로_다르다() {
        // 다르지 않으면 rotation 을 해도 이전 토큰이 새 토큰과 같아서 계속 유효합니다.
        assertThat(provider.createRefreshToken(USER_ID)).isNotEqualTo(provider.createRefreshToken(USER_ID));
    }

    @Test
    void 형식이_깨진_토큰은_예외없이_비어_있다() {
        assertThat(provider.parseAccessUser("이건 토큰이 아닙니다")).isEmpty();
        assertThat(provider.parseRefreshUserId("")).isEmpty();
    }

    @Test
    void 만료된_토큰은_비어_있다() {
        JwtTokenProvider expiring = new JwtTokenProvider(new JwtProperties(SECRET, -1000L, -1000L));

        assertThat(provider.parseAccessUser(expiring.createAccessToken(USER_ID))).isEmpty();
        assertThat(provider.parseRefreshUserId(expiring.createRefreshToken(USER_ID))).isEmpty();
    }

    @Test
    void 다른_키로_서명한_토큰은_비어_있다() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-that-is-long-enough-0123456789".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("1")
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThat(provider.parseAccessUser(forged)).isEmpty();
    }

    @Test
    void subject_가_숫자가_아니어도_예외없이_비어_있다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String weird = Jwts.builder()
                .subject("not-a-number")
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThat(provider.parseAccessUser(weird)).isEmpty();
    }
}
