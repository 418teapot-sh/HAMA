package com.hama.domain.auth.service;

import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.auth.JwtTokenProvider;
import com.hama.domain.auth.dto.LoginRequest;
import com.hama.domain.auth.dto.SignupRequest;
import com.hama.domain.auth.entity.RefreshToken;
import com.hama.domain.auth.exception.AuthErrorCode;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final RefreshTokenWriter refreshTokenWriter;

    /** 가입과 동시에 로그인 처리합니다. */
    @Transactional
    public AuthTokens signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.nickname());
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
        loginAttemptLimiter.checkAllowed(request.email());

        User user = userRepository.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        loginAttemptLimiter.onSuccess(request.email());
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

        RefreshToken saved = refreshTokenRepository.findByUserId(userId)
                .filter(token -> RefreshTokenHasher.matches(refreshToken, token.getTokenHash()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        // 토큰은 멀쩡한데 유저가 사라졌으면(탈퇴) 재발급하지 않습니다.
        if (!userRepository.existsById(userId)) {
            refreshTokenRepository.delete(saved);
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        saved.rotate(RefreshTokenHasher.hash(newRefreshToken));
        return new AuthTokens(jwtTokenProvider.createAccessToken(userId), newRefreshToken);
    }

    /** 토큰이 없거나 이미 무효여도 에러 없이 끝납니다(멱등). 쿠키 삭제는 컨트롤러가 항상 합니다. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        jwtTokenProvider.parseRefreshUserId(refreshToken)
                .flatMap(refreshTokenRepository::findByUserId)
                .filter(saved -> RefreshTokenHasher.matches(refreshToken, saved.getTokenHash()))
                .ifPresent(refreshTokenRepository::delete);
    }

    private AuthTokens issueTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        saveRefreshToken(userId, RefreshTokenHasher.hash(refreshToken));
        return new AuthTokens(accessToken, refreshToken);
    }

    /**
     * 기존 행이 있으면 같은 트랜잭션에서 갱신합니다(UPDATE 라 unique 제약에 안 걸림).
     * 없으면 INSERT 를 {@link RefreshTokenWriter} 의 별도 트랜잭션에 맡겨서, 같은 유저의 동시 로그인으로
     * unique 제약에 걸려도 이 트랜잭션은 오염되지 않고 재조회로 복구합니다.
     */
    private void saveRefreshToken(Long userId, String tokenHash) {
        Optional<RefreshToken> existing = refreshTokenRepository.findByUserId(userId);
        if (existing.isPresent()) {
            existing.get().rotate(tokenHash);
            return;
        }

        try {
            refreshTokenWriter.insert(userId, tokenHash);
        } catch (DataIntegrityViolationException e) {
            refreshTokenRepository.findByUserId(userId)
                    .orElseThrow(() -> e)
                    .rotate(tokenHash);
        }
    }
}
