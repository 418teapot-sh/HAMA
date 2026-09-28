package com.hama.global.auth;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 저장소에 커밋된 JWT 시크릿으로 배포 환경이 뜨는 것을 막습니다.
 *
 * <p>{@code application.yml} 은 {@code jwt.secret: ${JWT_SECRET}} 처럼 기본값을 두지 않아서
 * 환경변수를 <b>빠뜨리면</b> 부팅이 실패합니다. 하지만 서버를 세팅하면서
 * {@code application-local.yml} 의 값을 그대로 복사해 넣는 실수는 못 막습니다.
 * 그러면 아무 에러 없이 정상 부팅하는데, 리포지터리를 볼 수 있는 누구나 서명 키를 알아서
 * 아무 userId 로나 토큰을 만들 수 있습니다.
 *
 * <p>그래서 {@code local} · {@code test} 가 아닌 프로필로 뜰 때 커밋된 값이면 부팅을 세웁니다.
 * 목록에 없는 값은 아무리 약해도 통과합니다. (짧은 키는 {@code Keys.hmacShaKeyFor} 가
 * {@code WeakKeyException} 으로 이미 막습니다)
 */
@Slf4j
@Component
public class JwtSecretGuard {

    /** 저장소에 그대로 들어 있는 시크릿들. yml 값을 바꾸면 여기도 바꿔야 JwtSecretGuardTest 가 통과합니다. */
    static final Set<String> COMMITTED_SECRETS = Set.of(
            "local-only-dummy-secret-key-for-development-do-not-use-in-production", // application-local.yml
            "test-only-secret-key-do-not-use-in-production-0123456789"              // application-test.yml
    );

    /** 커밋된 값을 써도 되는 프로필. 개발자 PC 와 CI 입니다. */
    private static final Profiles DEVELOPMENT_PROFILES = Profiles.of("local", "test");

    public JwtSecretGuard(JwtProperties properties, Environment environment) {
        // 활성 프로필이 없으면 spring.profiles.default(= local)로 판단합니다.
        if (environment.acceptsProfiles(DEVELOPMENT_PROFILES)) {
            return;
        }

        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 이 비어 있습니다. dev 는 JWT_SECRET 환경변수로 주입해야 합니다.");
        }
        if (COMMITTED_SECRETS.contains(secret)) {
            throw new IllegalStateException("""
                    JWT_SECRET 이 저장소에 커밋된 값입니다. 이 키를 아는 사람은 누구나 토큰을 위조할 수 있습니다.
                    /etc/hama/app.env 의 JWT_SECRET 을 외부에 공개되지 않은 값으로 바꾸고 다시 배포하세요.
                    (활성 프로필: %s)""".formatted(String.join(", ", environment.getActiveProfiles())));
        }

        log.info("JWT 시크릿 확인 완료 — 커밋된 값이 아닙니다.");
    }
}
