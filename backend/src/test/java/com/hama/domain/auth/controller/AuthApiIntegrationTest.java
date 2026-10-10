package com.hama.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.auth.entity.RefreshToken;
import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.domain.auth.service.AuthService;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프론트와의 인증 계약(쿠키 속성, body 에 무엇이 오는지, 401 형식)을 실제 필터 체인 + MySQL 로 확인합니다.
 * 테스트끼리 데이터가 섞이지 않도록 매번 새 이메일로 가입합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    private static final Pattern REFRESH_COOKIE = Pattern.compile("refreshToken=([^;]*)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthService authService;

    private record Session(String email, String accessToken, String refreshToken) {
    }

    private Session signup() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@hama.com";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "password1234!", "name": "하마", "termsAgreed": true, "privacyAgreed": true, "ageConfirmed": true }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(email, accessTokenOf(result), refreshCookieOf(result));
    }

    private String accessTokenOf(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asString();
    }

    private static String refreshCookieOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        Matcher matcher = REFRESH_COOKIE.matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void 회원가입하면_리프레시_토큰이_HttpOnly_쿠키로_온다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "user-%s@hama.com", "password": "password1234!", "name": "하마", "termsAgreed": true, "privacyAgreed": true, "ageConfirmed": true }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("refreshToken="),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("Max-Age=1209600"))))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    /** 디자인 규칙: 영문·숫자·특수문자를 각각 포함한 8~20자. 공백·한글은 받지 않습니다. */
    @ParameterizedTest
    @ValueSource(strings = {"pass12!", "password1234!password", "password1234", "password!!!!", "12345678!",
            "pass word1!", "abc한글123!", "password12·"})
    void 비밀번호_규칙에_맞지_않으면_400(String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "user-%s@hama.com", "password": "%s", "name": "하마", "termsAgreed": true, "privacyAgreed": true, "ageConfirmed": true }
                                """.formatted(UUID.randomUUID(), password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.password").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd123!", "Abcdefghij1234567@#~"})
    void 비밀번호_규칙의_경계값은_가입된다(String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "user-%s@hama.com", "password": "%s", "name": "하마", "termsAgreed": true, "privacyAgreed": true, "ageConfirmed": true }
                                """.formatted(UUID.randomUUID(), password)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"termsAgreed\": false, \"privacyAgreed\": true, \"ageConfirmed\": true",
            "\"termsAgreed\": true, \"privacyAgreed\": true"})
    void 필수_약관에_동의하지_않으면_가입되지_않는다(String agreements) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@hama.com";

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "password1234!", "name": "하마", %s }
                                """.formatted(email, agreements)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void 마케팅_수신_동의는_선택이고_생략하면_false_로_저장된다() throws Exception {
        String withMarketing = signupWith("\"marketingAgreed\": true");
        String withoutMarketing = signupWith("\"marketingAgreed\": null");

        assertThat(userRepository.findByEmail(withMarketing).orElseThrow().isMarketingAgreed()).isTrue();
        assertThat(userRepository.findByEmail(withoutMarketing).orElseThrow().isMarketingAgreed()).isFalse();
    }

    private String signupWith(String marketing) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@hama.com";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "password1234!", "name": "하마",
                                  "termsAgreed": true, "privacyAgreed": true, "ageConfirmed": true, %s }
                                """.formatted(email, marketing)))
                .andExpect(status().isOk());
        return email;
    }

    @Test
    void 로그인_응답_body_에는_accessToken_만_있다() throws Exception {
        Session session = signup();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "password1234!" }
                                """.formatted(session.email())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void Bearer_없이_보호된_API_를_부르면_401_ApiResponse() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void Bearer_를_붙이면_200() throws Exception {
        Session session = signup();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(session.email()))
                .andExpect(jsonPath("$.data.isPremium").value(false));
    }

    @Test
    void 쿠키만으로_재발급하면_새_토큰과_새_쿠키가_오고_이전_쿠키는_무효가_된다() throws Exception {
        Session session = signup();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String rotated = refreshCookieOf(result);
        assertThat(rotated).isNotEqualTo(session.refreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 쿠키_없이_재발급하면_401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 리프레시_토큰으로_Bearer_인증하면_401() throws Exception {
        Session session = signup();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 로그아웃하면_쿠키가_지워지고_DB_에서도_삭제된다() throws Exception {
        Session session = signup();
        Long userId = userRepository.findByEmail(session.email()).orElseThrow().getId();
        assertThat(refreshTokenCount(userId)).isEqualTo(1);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("refreshToken=;"),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"))));

        assertThat(refreshTokenCount(userId)).isZero();
    }

    @Test
    void 여러_기기에서_로그인해도_각자_재발급된다() throws Exception {
        Session pc = signup();
        MvcResult phoneLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "password1234!" }
                                """.formatted(pc.email())))
                .andExpect(status().isOk())
                .andReturn();
        String phoneRefresh = refreshCookieOf(phoneLogin);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", pc.refreshToken())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", phoneRefresh)))
                .andExpect(status().isOk());
    }

    @Test
    void 만료된_리프레시_토큰_행은_정리된다() throws Exception {
        Session expired = signup();
        Session alive = signup();
        Long expiredUserId = userRepository.findByEmail(expired.email()).orElseThrow().getId();
        Long aliveUserId = userRepository.findByEmail(alive.email()).orElseThrow().getId();
        // 마지막 발급 시각을 15일 전으로 돌려서 만료된 행으로 만듭니다.
        RefreshToken token = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(expiredUserId)).findFirst().orElseThrow();
        refreshTokenRepository.rotate(expiredUserId, token.getTokenHash(), token.getTokenHash(),
                LocalDateTime.now().minusDays(15));

        authService.deleteExpiredRefreshTokens();

        assertThat(refreshTokenCount(expiredUserId)).isZero();
        assertThat(refreshTokenCount(aliveUserId)).isEqualTo(1);
    }

    private long refreshTokenCount(Long userId) {
        return refreshTokenRepository.findAll().stream().filter(token -> token.getUserId().equals(userId)).count();
    }

    @Test
    void 공개_경로는_토큰_없이_열린다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void 허용된_Origin_이면_credentials_를_허용한다() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
