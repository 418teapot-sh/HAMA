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
 * 키(로그인은 이메일, 탈퇴는 {@code withdraw:userId})당 비밀번호 시도 횟수를 세서 무차별 대입(brute force)을 막습니다.
 *
 * <p>단일 서버(EC2 한 대) 구성이라 인메모리로 충분하고, 서버 재시작 시 초기화돼도 괜찮습니다.
 * 서버를 여러 대로 늘리면 서버마다 따로 세므로 Redis 등으로 옮겨야 합니다.
 *
 * <p>검사와 증가를 하나의 {@code compute} 안에서 원자적으로 처리해서, 병렬 요청이 검사를
 * 동시에 통과하는 것을 막습니다.
 */
@Component
public class LoginAttemptLimiter {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(AtomicInteger count, Instant windowStart) {
        boolean isExpired() {
            return Instant.now().isAfter(windowStart.plus(WINDOW));
        }
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    void checkAllowed(String email) {
        if (!tryAcquire(email)) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    /** 시도 하나를 셉니다. 창(15분) 안에서 이미 {@link #MAX_ATTEMPTS} 번 시도했으면 세지 않고 false 입니다. */
    public boolean tryAcquire(String key) {
        AtomicBoolean blocked = new AtomicBoolean(false);
        attempts.compute(key, (ignored, existing) -> {
            Attempt current = (existing == null || existing.isExpired())
                    ? new Attempt(new AtomicInteger(0), Instant.now())
                    : existing;
            if (current.count().get() >= MAX_ATTEMPTS) {
                blocked.set(true);
            } else {
                current.count().incrementAndGet();
            }
            return current;
        });
        return !blocked.get();
    }

    void onSuccess(String email) {
        attempts.remove(email);
    }

    /** 틀리고 다시 오지 않은 이메일이 맵에 계속 쌓이지 않도록 주기적으로 비웁니다. */
    @Scheduled(fixedRate = 15, initialDelay = 15, timeUnit = TimeUnit.MINUTES)
    void evictExpired() {
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
