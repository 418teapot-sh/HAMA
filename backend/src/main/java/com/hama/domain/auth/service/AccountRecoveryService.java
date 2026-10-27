package com.hama.domain.auth.service;

import com.hama.domain.auth.dto.EmailInquiryRequest;
import com.hama.domain.auth.dto.PasswordResetConfirmRequest;
import com.hama.domain.auth.entity.EmailInquiry;
import com.hama.domain.auth.entity.PasswordResetToken;
import com.hama.domain.auth.exception.AuthErrorCode;
import com.hama.domain.auth.repository.EmailInquiryRepository;
import com.hama.domain.auth.repository.PasswordResetTokenRepository;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인하지 못하는 사용자를 위한 비밀번호 재설정과 이메일 찾기 문의입니다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailInquiryRepository inquiryRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordAttemptLimiter limiter;
    private final PasswordResetLinkIssuer linkIssuer;
    private final AccountMailSender mailSender;

    /**
     * 가입된 이메일이면 재설정 링크를 메일로 보냅니다. 가입 여부를 숨기려고 없는 이메일이어도 똑같이 성공으로 끝납니다.
     * 다시 요청하면 새 링크를 보내고 옛 링크는 무효가 됩니다.
     *
     * <p>메일 폭탄과 Resend 하루 발송 한도 소진을 막으려고 받는 이메일·요청 IP 마다 15분에 5번까지 받습니다.
     * 이메일 키는 가입 여부와 상관없이 세므로 429 로 가입 여부가 드러나지 않습니다.
     * 가입 여부 확인·토큰 저장·메일은 응답과 따로 돌려서(PasswordResetLinkIssuer) 응답 시간이 가입 여부와 상관없습니다.
     */
    public void requestPasswordReset(String email, String clientIp) {
        // IP 를 먼저 셉니다. 이메일을 먼저 세면 이미 막힌 IP 가 아무 이메일로나 요청해 남의 이메일 몫을 깎고, 카운터 맵에 키를 계속 쌓습니다.
        limiter.check("reset-ip:" + clientIp, AuthErrorCode.TOO_MANY_REQUESTS);
        limiter.check("reset-email:" + email, AuthErrorCode.TOO_MANY_REQUESTS);
        linkIssuer.issue(email);
    }

    /**
     * 링크의 토큰으로 비밀번호를 바꿉니다. 링크는 한 번만 쓸 수 있고, 바꾸면 모든 기기에서 로그아웃됩니다
     * (비밀번호를 잊은 게 아니라 털려서 바꾸는 경우, 남이 들고 있는 리프레시 토큰을 끊어야 해서).
     */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String tokenHash = RefreshTokenHasher.hash(request.token());
        PasswordResetToken resetToken = resetTokenRepository
                .findByTokenHashAndExpiresAtAfter(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN));
        // 같은 링크로 동시에 두 번 오면 먼저 지운 쪽만 진행합니다.
        if (resetTokenRepository.deleteByTokenHash(tokenHash) == 0) {
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        // 링크를 받은 뒤 탈퇴했으면 탈퇴가 토큰 행도 지우지만, 그 사이에 끼면 사용자가 없을 수 있습니다.
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN));
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.deleteAllByUserId(user.getId());
        log.info("[PasswordReset] 비밀번호 재설정 userId={}", user.getId());
    }

    /**
     * 이메일 찾기 문의를 저장하고 운영자에게 알림 메일을 보냅니다. 스팸을 막으려고 IP 마다 15분에 5번까지 받습니다.
     * 트랜잭션을 걸지 않아 save 가 바로 커밋되고, 그 뒤에 알림을 보냅니다(저장에 실패한 문의로 알림이 가지 않게).
     */
    public void submitEmailInquiry(EmailInquiryRequest request, String clientIp) {
        limiter.check("inquiry-ip:" + clientIp, AuthErrorCode.TOO_MANY_REQUESTS);
        inquiryRepository.save(EmailInquiry.create(request.name(), request.email()));
        mailSender.sendEmailInquiryNotice(request.name(), request.email());
    }
}
