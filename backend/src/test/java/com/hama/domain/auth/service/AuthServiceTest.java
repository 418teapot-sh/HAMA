package com.hama.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hama.domain.auth.dto.LoginRequest;
import com.hama.domain.auth.dto.SignupRequest;
import com.hama.domain.auth.exception.AuthErrorCode;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.auth.JwtProperties;
import com.hama.global.auth.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        JwtProperties properties = new JwtProperties(SECRET, 30 * 60 * 1000L, 14L * 24 * 60 * 60 * 1000);
        jwtTokenProvider = new JwtTokenProvider(properties);
        authService = new AuthService(jwtTokenProvider, refreshTokenRepository, userRepository, passwordEncoder,
                new PasswordAttemptLimiter(), properties);
    }

    private static User savedUser() {
        User user = User.create(EMAIL, "encoded", "하마", false, LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Nested
    class 회원가입 {

        @Test
        void 무료_상태로_가입하고_토큰을_발급한다() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode("password1234!")).willReturn("encoded");
            given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                assertThat(user.isPremium()).isFalse();
                assertThat(user.getGoalCreatedCount()).isZero();
                assertThat(user.getPassword()).isEqualTo("encoded");
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            AuthTokens tokens = authService.signup(new SignupRequest(EMAIL, "password1234!", "하마", true, true, true, null));

            assertThat(jwtTokenProvider.parseAccessUser(tokens.accessToken())).isPresent();
            assertThat(jwtTokenProvider.parseRefreshUserId(tokens.refreshToken())).contains(1L);
        }

        @Test
        void 이미_가입된_이메일이면_409() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            assertThatThrownBy(() -> authService.signup(new SignupRequest(EMAIL, "password1234!", "하마", true, true, true, null)))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @Test
        void 이메일은_소문자로_정규화한다() {
            assertThat(new SignupRequest(" Test@HAMA.com ", "password1234!", "하마", true, true, true, null).email()).isEqualTo(EMAIL);
        }
    }

    @Nested
    class 로그인 {

        @Test
        void 비밀번호가_맞으면_토큰을_발급한다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(userRepository.findByIdForShare(1L)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("password1234!", "encoded")).willReturn(true);

            AuthTokens tokens = authService.login(new LoginRequest(EMAIL, "password1234!"));

            assertThat(tokens.accessToken()).isNotBlank();
        }

        @Test
        void 비밀번호가_맞아도_그사이_탈퇴했으면_실패한다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(userRepository.findByIdForShare(1L)).willReturn(Optional.empty());
            given(passwordEncoder.matches("password1234!", "encoded")).willReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "password1234!")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 없는_이메일과_틀린_비밀번호는_같은_에러다() {
            given(userRepository.findByEmail("unknown@hama.com")).willReturn(Optional.empty());
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@hama.com", "password1234!")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 다섯_번_틀리면_그_다음_시도는_잠긴다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            for (int i = 0; i < PasswordAttemptLimiter.MAX_ATTEMPTS; i++) {
                assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            }

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.TOO_MANY_PASSWORD_ATTEMPTS);
        }
    }

    @Nested
    class 재발급 {

        @Test
        void 저장된_토큰과_일치하면_새_토큰으로_교체한다() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            given(userRepository.existsById(1L)).willReturn(true);
            given(refreshTokenRepository.rotate(eq(1L), eq(RefreshTokenHasher.hash(refreshToken)), any(), any()))
                    .willReturn(1);

            AuthTokens tokens = authService.reissue(refreshToken);

            assertThat(tokens.refreshToken()).isNotEqualTo(refreshToken);
            verify(refreshTokenRepository).rotate(eq(1L), eq(RefreshTokenHasher.hash(refreshToken)),
                    eq(RefreshTokenHasher.hash(tokens.refreshToken())), any());
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
            given(userRepository.existsById(1L)).willReturn(true);
            // 교체된 행이 없으면(0) 이미 교체됐거나 로그아웃된 토큰입니다.
            given(refreshTokenRepository.rotate(any(), any(), any(), any())).willReturn(0);

            assertThatThrownBy(() -> authService.reissue(refreshToken))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Nested
    class 로그아웃 {

        @Test
        void 이_기기의_토큰만_삭제한다() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);

            authService.logout(refreshToken);

            verify(refreshTokenRepository).deleteByUserIdAndTokenHash(1L, RefreshTokenHasher.hash(refreshToken));
        }

        @Test
        void 토큰이_없거나_무효해도_예외없이_끝난다() {
            authService.logout(null);
            authService.logout("not-a-jwt");
        }
    }
}
