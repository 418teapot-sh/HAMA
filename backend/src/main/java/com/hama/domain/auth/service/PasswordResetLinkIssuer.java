package com.hama.domain.auth.service;

import com.hama.domain.auth.repository.PasswordResetTokenRepository;
import com.hama.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재설정 링크를 만들어 메일로 보냅니다. 응답과 따로(@Async) 돌아서, 가입된 이메일만 DB 쓰기·메일 시간만큼 응답이 느려져
 * 응답 시간으로 가입 여부가 드러나는 일이 없습니다. 메일은 토큰 저장이 커밋된 뒤에 보내므로 DB 에 없는 링크가 나가지 않습니다.
 *
 * <p>ponytail: 같은 이메일로 연달아 요청하면 비동기라 메일 도착 순서가 뒤바뀌어, 늦게 온 옛 링크가 무효일 수 있습니다.
 * 이메일당 15분 5번 제한이라 드물고 다시 요청하면 됩니다. 문제가 되면 이메일별로 줄을 세우세요.
 */
@Component
@RequiredArgsConstructor
class PasswordResetLinkIssuer {

    static final Duration VALIDITY = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AccountMailSender mailSender;
    private final TransactionTemplate transactionTemplate;

    /**
     * 다시 요청하면 같은 행의 해시를 바꿔 옛 링크를 무효로 합니다. 없는 이메일이면 아무것도 하지 않습니다.
     * 메일은 요청에 적힌 주소가 아니라 저장된 가입 이메일로 보냅니다. DB 정렬 규칙(악센트 무시)이 비슷하게 생긴
     * 다른 주소(예: 국제화 도메인)를 같은 계정으로 찾아 주더라도 링크가 그 주소로 새지 않게 하려는 것입니다.
     */
    @Async
    public void issue(String email) {
        String rawToken = newToken();
        String to = transactionTemplate.execute(status -> userRepository.findByEmail(email)
                .map(user -> {
                    LocalDateTime now = LocalDateTime.now();
                    resetTokenRepository.upsert(user.getId(), TokenHasher.hash(rawToken), now.plus(VALIDITY), now);
                    return user.getEmail();
                })
                .orElse(null));
        if (to != null) {
            mailSender.sendPasswordReset(to, rawToken);
        }
    }

    /** 256비트 난수. URL 에 그대로 넣을 수 있게 URL-safe Base64(패딩 없음)로 만듭니다. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
