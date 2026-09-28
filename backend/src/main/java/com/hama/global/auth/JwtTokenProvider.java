package com.hama.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

    /**
     * 액세스/리프레시를 구분하는 claim 입니다. 두 토큰은 같은 키로 서명되므로 서명 검증만으로는
     * 구분되지 않습니다. 이 값을 안 보면 14일짜리 리프레시 토큰으로 API 인증이 통과합니다.
     */
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final SecretKey key;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, ACCESS_TOKEN, accessTokenValidity).compact();
    }

    /**
     * 리프레시 토큰에는 jti(랜덤 id)를 넣습니다. 없으면 같은 초에 두 번 발급한 토큰이
     * 글자 하나 다르지 않게 똑같아서, 재발급(rotation)을 해도 이전 토큰이 계속 유효합니다.
     */
    public String createRefreshToken(Long userId) {
        return createToken(userId, REFRESH_TOKEN, refreshTokenValidity)
                .id(UUID.randomUUID().toString())
                .compact();
    }

    private JwtBuilder createToken(Long userId, String tokenType, long validityMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMillis))
                .signWith(key);
    }

    /**
     * 액세스 토큰을 <b>한 번만 파싱해서</b> 인증 정보를 만듭니다.
     * 검증 → 타입 확인 → userId 추출을 메서드마다 따로 하면 요청마다 HMAC 검증이 여러 번 돕니다.
     *
     * @return 유효한 <b>액세스</b> 토큰이면 인증 정보, 아니면 비어 있음 (예외를 던지지 않습니다)
     */
    public Optional<AuthUser> parseAccessUser(String token) {
        return parseUserId(token, ACCESS_TOKEN).map(AuthUser::new);
    }

    /**
     * @return 유효한 <b>리프레시</b> 토큰이면 userId, 아니면 비어 있음 (예외를 던지지 않습니다)
     */
    public Optional<Long> parseRefreshUserId(String token) {
        return parseUserId(token, REFRESH_TOKEN);
    }

    /**
     * 실패 이유는 여기서 로그로 남깁니다. 요청 경로는 없지만 TraceIdFilter 가 MDC 에 넣은
     * traceId 가 같이 찍혀서 같은 요청의 다른 로그와 이어집니다.
     */
    private Optional<Long> parseUserId(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!expectedType.equals(tokenType)) {
                log.warn("{} 토큰 자리에 {} 토큰이 들어왔습니다.", expectedType, tokenType);
                return Optional.empty();
            }
            // subject 가 숫자가 아니면 NumberFormatException(IllegalArgumentException) 이라 아래에서 걸립니다.
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰입니다.");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 토큰입니다: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
