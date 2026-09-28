package com.hama.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hama.domain.auth.dto.LoginRequest;
import com.hama.domain.auth.dto.SignupRequest;
import com.hama.domain.auth.entity.RefreshToken;
import com.hama.domain.auth.exception.AuthErrorCode;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.auth.JwtProperties;
import com.hama.global.auth.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "test-only-secret-key-do-not-use-in-production-0123456789";
    private static final String EMAIL = "test@hama.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, 30 * 60 * 1000L, 14L * 24 * 60 * 60 * 1000));
        authService = new AuthService(jwtTokenProvider, refreshTokenRepository, userRepository, passwordEncoder,
                new LoginAttemptLimiter(), new RefreshTokenWriter(refreshTokenRepository));
    }

    private static User savedUser() {
        User user = User.create(EMAIL, "encoded", "닉네임");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Nested
    class 회원가입 {

        @Test
        void 무료_상태로_가입하고_토큰을_발급한다() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode("password1234")).willReturn("encoded");
            given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                assertThat(user.isPremium()).isFalse();
                assertThat(user.getGoalCreatedCount()).isZero();
                assertThat(user.getPassword()).isEqualTo("encoded");
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            AuthTokens tokens = authService.signup(new SignupRequest(EMAIL, "password1234", "닉네임"));

            assertThat(jwtTokenProvider.parseAccessUser(tokens.accessToken())).isPresent();
            assertThat(jwtTokenProvider.parseRefreshUserId(tokens.refreshToken())).contains(1L);
        }

        @Test
        void 이미_가입된_이메일이면_409() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            assertThatThrownBy(() -> authService.signup(new SignupRequest(EMAIL, "password1234", "닉네임")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @Test
        void 이메일은_소문자로_정규화한다() {
            assertThat(new SignupRequest(" Test@HAMA.com ", "password1234", "닉네임").email()).isEqualTo(EMAIL);
        }

        @Test
        void 리프레시_토큰_INSERT가_동시성으로_실패해도_재조회로_복구한다() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode("password1234")).willReturn("encoded");
            given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });
            RefreshToken concurrentlyInserted = RefreshToken.create(1L, "other-session-hash");
            given(refreshTokenRepository.findByUserId(1L))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(concurrentlyInserted));
            given(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            AuthTokens tokens = authService.signup(new SignupRequest(EMAIL, "password1234", "닉네임"));

            assertThat(RefreshTokenHasher.matches(tokens.refreshToken(), concurrentlyInserted.getTokenHash())).isTrue();
        }
    }

    @Nested
    class 로그인 {

        @Test
        void 비밀번호가_맞으면_토큰을_발급한다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("password1234", "encoded")).willReturn(true);

            AuthTokens tokens = authService.login(new LoginRequest(EMAIL, "password1234"));

            assertThat(tokens.accessToken()).isNotBlank();
        }

        @Test
        void 없는_이메일과_틀린_비밀번호는_같은_에러다() {
            given(userRepository.findByEmail("unknown@hama.com")).willReturn(Optional.empty());
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@hama.com", "password1234")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 다섯_번_틀리면_그_다음_시도는_잠긴다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS; i++) {
                assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            }

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    @Nested
    class 재발급 {

        @Test
        void 저장된_토큰과_일치하면_새_토큰으로_교체한다() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            RefreshToken saved = RefreshToken.create(1L, RefreshTokenHasher.hash(refreshToken));
            given(refreshTokenRepository.findByUserId(1L)).willReturn(Optional.of(saved));
            given(userRepository.existsById(1L)).willReturn(true);

            AuthTokens tokens = authService.reissue(refreshToken);

            assertThat(tokens.refreshToken()).isNotEqualTo(refreshToken);
            assertThat(RefreshTokenHasher.matches(tokens.refreshToken(), saved.getTokenHash())).isTrue();
            assertThat(RefreshTokenHasher.matches(refreshToken, saved.getTokenHash()))
                    .as("rotation 후 이전 토큰은 더 이상 맞지 않아야 합니다")
                    .isFalse();
        }

        @Test
        void 쿠키가_없으면_401() {
            assertThatThrownBy(() -> authService.reissue(null))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        void 액세스_토큰으로_시도하면_401() {
            String accessToken = jwtTokenProvider.createAccessToken(1L);

            assertThatThrownBy(() -> authService.reissue(accessToken))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        void 저장된_해시와_다르면_401() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            given(refreshTokenRepository.findByUserId(1L))
                    .willReturn(Optional.of(RefreshToken.create(1L, RefreshTokenHasher.hash("다른-토큰"))));

            assertThatThrownBy(() -> authService.reissue(refreshToken))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Nested
    class 로그아웃 {

        @Test
        void 저장된_토큰과_일치하면_삭제한다() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            RefreshToken saved = RefreshToken.create(1L, RefreshTokenHasher.hash(refreshToken));
            given(refreshTokenRepository.findByUserId(1L)).willReturn(Optional.of(saved));

            authService.logout(refreshToken);

            verify(refreshTokenRepository).delete(saved);
        }

        @Test
        void 토큰이_없거나_무효해도_예외없이_끝난다() {
            authService.logout(null);
            authService.logout("not-a-jwt");
        }
    }
}
