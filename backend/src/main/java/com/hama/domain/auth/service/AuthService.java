package com.hama.domain.auth.service;

import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.auth.JwtProperties;
import com.hama.global.auth.JwtTokenProvider;
import com.hama.domain.auth.dto.LoginRequest;
import com.hama.domain.auth.dto.SignupRequest;
import com.hama.domain.auth.entity.RefreshToken;
import com.hama.domain.auth.exception.AuthErrorCode;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.global.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordAttemptLimiter attemptLimiter;
    private final JwtProperties jwtProperties;

    /** 가입과 동시에 로그인 처리합니다. */
    @Transactional
    public AuthTokens signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.name(),
                Boolean.TRUE.equals(request.marketingAgreed()), LocalDateTime.now());
        try {
            // 위 검사와 저장 사이에 같은 이메일이 먼저 들어오면 unique 제약에 걸립니다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return issueTokens(user.getId());
    }

    /**
     * 이메일이 없는 경우와 비밀번호가 틀린 경우 모두 {@code INVALID_CREDENTIALS} 입니다.
     * 어느 쪽인지 알려주면 로그인 화면이 가입 여부 조회기가 됩니다.
     */
    @Transactional
    public AuthTokens login(LoginRequest request) {
        attemptLimiter.check(request.email());

        User user = userRepository.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
        // 비밀번호 확인(bcrypt) 뒤에 사용자 행만 공유 락으로 다시 읽어 동시에 진행 중인 탈퇴와 순서를 맞춥니다.
        // 탈퇴가 먼저면 행이 없어 실패하고, 로그인이 먼저면 탈퇴가 이 토큰까지 지웁니다.
        // 이메일 인덱스로 잠그면 탈퇴(기본키 → 이메일 인덱스)와 순서가 엇갈려 교착이 나므로 기본키로만 잠급니다.
        userRepository.findByIdForShare(user.getId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        attemptLimiter.onSuccess(request.email());
        return issueTokens(user.getId());
    }

    /**
     * 액세스·리프레시 토큰을 둘 다 새로 발급합니다(rotation). 이전 리프레시 토큰은 DB 의 해시가
     * 바뀌어서 더 이상 통과하지 못합니다.
     */
    @Transactional
    public AuthTokens reissue(String refreshToken) {
        Long userId = Optional.ofNullable(refreshToken)
                .flatMap(jwtTokenProvider::parseRefreshUserId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        // 토큰은 멀쩡한데 유저가 사라졌으면(탈퇴) 재발급하지 않습니다.
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 저장된 해시가 지금 받은 토큰일 때만 교체합니다. 같은 토큰으로 동시에 재발급하면 하나만 성공하고
        // 나머지는 401 이라, DB 와 브라우저 쿠키가 서로 다른 토큰을 들고 어긋나지 않습니다.
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        int rotated = refreshTokenRepository.rotate(userId, RefreshTokenHasher.hash(refreshToken),
                RefreshTokenHasher.hash(newRefreshToken), LocalDateTime.now());
        if (rotated == 0) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new AuthTokens(jwtTokenProvider.createAccessToken(userId), newRefreshToken);
    }

    /** 이 기기의 토큰만 지웁니다. 토큰이 없거나 이미 무효여도 에러 없이 끝납니다(멱등). 쿠키 삭제는 컨트롤러가 항상 합니다. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        jwtTokenProvider.parseRefreshUserId(refreshToken).ifPresent(userId ->
                refreshTokenRepository.deleteByUserIdAndTokenHash(userId, RefreshTokenHasher.hash(refreshToken)));
    }

    /** 기기마다 새 행을 넣습니다. 새 행이라 같은 유저가 동시에 로그인해도 서로 충돌하지 않습니다. */
    private AuthTokens issueTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenRepository.save(RefreshToken.create(userId, RefreshTokenHasher.hash(refreshToken)));
        return new AuthTokens(accessToken, refreshToken);
    }

    /**
     * 로그아웃 없이 떠난 기기의 행이 쌓이지 않도록 하루 한 번 만료된 행을 지웁니다.
     *
     * <p>로그인 안에서 지우면 같은 유저의 동시 로그인끼리 DELETE 의 갭 락과 INSERT 가 엇갈려 교착이 나서
     * 요청 흐름 밖으로 뺐습니다. 이 메서드는 트랜잭션 없이 돌고, 1000개 묶음마다 따로 커밋해서
     * 락을 오래 쥐지 않습니다(RefreshTokenRepository.deleteExpiredBatch).
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteExpiredRefreshTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(jwtProperties.refreshTokenValidity()));
        while (refreshTokenRepository.deleteExpiredBatch(cutoff) > 0) {
            // 지울 행이 없을 때까지 반복
        }
    }
}
