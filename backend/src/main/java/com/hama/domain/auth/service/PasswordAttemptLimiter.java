package com.hama.domain.auth.service;

import com.hama.domain.auth.exception.AuthErrorCode;
import com.hama.global.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 키(로그인은 이메일+IP, 탈퇴는 {@code withdraw:userId})당 비밀번호 시도 횟수를 세서 무차별 대입(brute force)을 막습니다.
 * 재설정 메일·이메일 찾기 문의도 메일 폭탄을 막으려고 같은 방식으로 셉니다(AccountRecoveryService).
 *
 * <p>단일 서버(EC2 한 대) 구성이라 인메모리로 충분하고, 서버 재시작 시 초기화돼도 괜찮습니다.
 * 서버를 여러 대로 늘리면 서버마다 따로 세므로 Redis 등으로 옮겨야 합니다.
 *
 * <p>검사와 증가를 하나의 {@code compute} 안에서 원자적으로 처리해서, 병렬 요청이 검사를
 * 동시에 통과하는 것을 막습니다.
 */
@Component
public class PasswordAttemptLimiter {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(AtomicInteger count, Instant windowStart) {
        boolean isExpired() {
            return Instant.now().isAfter(windowStart.plus(WINDOW));
        }
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 시도 하나를 셉니다. 창(15분) 안에서 이미 {@link #MAX_ATTEMPTS} 번 시도했으면 429 를 던집니다. */
    public void check(String key) {
        check(key, AuthErrorCode.TOO_MANY_PASSWORD_ATTEMPTS);
    }

    /** 비밀번호가 아닌 요청(재설정 메일·이메일 찾기 문의)도 같은 방식으로 세고, 넘으면 {@code errorCode} 로 429 를 던집니다. */
    public void check(String key, AuthErrorCode errorCode) {
        check(key, errorCode, MAX_ATTEMPTS);
    }

    /** IP 처럼 여러 사람이 나눠 쓸 수 있는 키는 {@link #MAX_ATTEMPTS} 보다 넉넉하게 셉니다. */
    public void check(String key, AuthErrorCode errorCode, int maxAttempts) {
        AtomicBoolean blocked = new AtomicBoolean(false);
        attempts.compute(key, (ignored, existing) -> {
            Attempt current = (existing == null || existing.isExpired())
                    ? new Attempt(new AtomicInteger(0), Instant.now())
                    : existing;
            if (current.count().get() >= maxAttempts) {
                blocked.set(true);
            } else {
                current.count().incrementAndGet();
            }
            return current;
        });
        if (blocked.get()) {
            throw new BusinessException(errorCode);
        }
    }

    void onSuccess(String key) {
        attempts.remove(key);
    }

    /**
     * 로그인 시도 키. 이메일만 키로 쓰면 남의 이메일로 5번 틀려서 그 계정을 잠글 수 있어서 접속 IP 를 함께 씁니다.
     * 형식을 바꾸면 {@link #clearLoginLocks} 도 같이 바뀌도록 여기 한 곳에서만 만듭니다.
     */
    static String loginKey(String email, String clientIp) {
        return email + "|" + clientIp;
    }

    /** 이 이메일의 로그인 잠금을 IP 와 상관없이 모두 풉니다(비밀번호 재설정 후). */
    void clearLoginLocks(String email) {
        String prefix = loginKey(email, "");
        attempts.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** 틀리고 다시 오지 않은 키가 맵에 계속 쌓이지 않도록 주기적으로 비웁니다. */
    @Scheduled(fixedRate = 15, initialDelay = 15, timeUnit = TimeUnit.MINUTES)
    void evictExpired() {
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
