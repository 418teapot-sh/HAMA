package com.hama.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hama.domain.auth.dto.LoginRequest;
import com.hama.domain.auth.dto.SignupRequest;
import com.hama.domain.auth.exception.AuthErrorCode;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.auth.JwtProperties;
import com.hama.global.auth.JwtTokenProvider;
import com.hama.global.exception.BusinessException;
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
    private static final String IP = "203.0.113.1";
    private static final AuthLimitProperties LIMITS = new AuthLimitProperties(30, 50);

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
                new PasswordAttemptLimiter(), properties, LIMITS);
    }

    private static User savedUser() {
        User user = User.create(EMAIL, "encoded", "하마", false);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Nested
    class 회원가입 {

        @Test
        void 무료_크레딧_1회로_가입하고_토큰을_발급한다() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode("password1234!")).willReturn("encoded");
            given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                assertThat(user.getCredits()).isEqualTo(1);
                assertThat(user.getPassword()).isEqualTo("encoded");
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            AuthTokens tokens = authService.signup(new SignupRequest(EMAIL, "password1234!", "하마", true, true, true, null), IP);

            assertThat(jwtTokenProvider.parseAccessUser(tokens.accessToken())).isPresent();
            assertThat(jwtTokenProvider.parseRefreshUserId(tokens.refreshToken())).contains(1L);
        }

        @Test
        void 이미_가입된_이메일이면_409() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            assertThatThrownBy(() -> authService.signup(new SignupRequest(EMAIL, "password1234!", "하마", true, true, true, null), IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @Test
        void 같은_IP_에서_정해진_횟수보다_많이_가입하면_429() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);
            SignupRequest request = new SignupRequest(EMAIL, "password1234!", "하마", true, true, true, null);
            for (int i = 0; i < LIMITS.signupPerIp(); i++) {
                assertThatThrownBy(() -> authService.signup(request, IP))
                        .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
            }

            assertThatThrownBy(() -> authService.signup(request, IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.TOO_MANY_REQUESTS);
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
            given(userRepository.findPasswordByIdForShare(1L)).willReturn(Optional.of("encoded"));
            given(passwordEncoder.matches("password1234!", "encoded")).willReturn(true);

            AuthTokens tokens = authService.login(new LoginRequest(EMAIL, "password1234!"), IP);

            assertThat(tokens.accessToken()).isNotBlank();
        }

        @Test
        void 비밀번호가_맞아도_그사이_탈퇴했으면_실패한다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(userRepository.findPasswordByIdForShare(1L)).willReturn(Optional.empty());
            given(passwordEncoder.matches("password1234!", "encoded")).willReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "password1234!"), IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 비밀번호가_맞아도_그사이_비밀번호가_재설정됐으면_실패한다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(userRepository.findPasswordByIdForShare(1L)).willReturn(Optional.of("encoded-after-reset"));
            given(passwordEncoder.matches("password1234!", "encoded")).willReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "password1234!"), IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void 없는_이메일과_틀린_비밀번호는_같은_에러다() {
            given(userRepository.findByEmail("unknown@hama.com")).willReturn(Optional.empty());
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@hama.com", "password1234!"), IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password"), IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void 다섯_번_틀리면_그_다음_시도는_잠긴다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);

            for (int i = 0; i < PasswordAttemptLimiter.MAX_ATTEMPTS; i++) {
                assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password"), IP))
                        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            }

            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password"), IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.TOO_MANY_PASSWORD_ATTEMPTS);
        }

        @Test
        void 다른_IP_에서_틀린_시도는_본인_로그인을_잠그지_않는다() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));
            given(passwordEncoder.matches("wrong-password", "encoded")).willReturn(false);
            given(passwordEncoder.matches("password1234!", "encoded")).willReturn(true);
            given(userRepository.findPasswordByIdForShare(1L)).willReturn(Optional.of("encoded"));

            for (int i = 0; i <= PasswordAttemptLimiter.MAX_ATTEMPTS; i++) {
                assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password"), "198.51.100.9"))
                        .isInstanceOf(BusinessException.class);
            }

            assertThat(authService.login(new LoginRequest(EMAIL, "password1234!"), IP)).isNotNull();
        }

        @Test
        void 이메일을_바꿔도_같은_IP_는_정해진_횟수를_넘으면_잠긴다() {
            for (int i = 0; i < LIMITS.loginPerIp(); i++) {
                String email = "user" + i + "@hama.com";
                assertThatThrownBy(() -> authService.login(new LoginRequest(email, "wrong-password"), IP))
                        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
            }

            assertThatThrownBy(() -> authService.login(new LoginRequest("other@hama.com", "wrong-password"), IP))
                    .extracting("errorCode").isEqualTo(AuthErrorCode.TOO_MANY_PASSWORD_ATTEMPTS);
        }
    }

    @Nested
    class 재발급 {

        @Test
        void 저장된_토큰과_일치하면_새_토큰으로_교체한다() {
            String refreshToken = jwtTokenProvider.createRefreshToken(1L);
            given(userRepository.existsById(1L)).willReturn(true);
            given(refreshTokenRepository.rotate(eq(1L), eq(TokenHasher.hash(refreshToken)), any(), any()))
                    .willReturn(1);

            AuthTokens tokens = authService.reissue(refreshToken);

            assertThat(tokens.refreshToken()).isNotEqualTo(refreshToken);
            verify(refreshTokenRepository).rotate(eq(1L), eq(TokenHasher.hash(refreshToken)),
                    eq(TokenHasher.hash(tokens.refreshToken())), any());
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

            verify(refreshTokenRepository).deleteByUserIdAndTokenHash(1L, TokenHasher.hash(refreshToken));
        }

        @Test
        void 토큰이_없거나_무효해도_예외없이_끝난다() {
            authService.logout(null);
            authService.logout("not-a-jwt");
        }
    }
}
