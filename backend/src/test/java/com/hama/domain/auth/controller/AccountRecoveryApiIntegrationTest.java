package com.hama.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.auth.repository.RefreshTokenRepository;
import com.hama.domain.auth.service.AccountMailSender;
import com.hama.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호 재설정·이메일 찾기 문의를 실제 필터 체인 + MySQL 로 확인합니다.
 * 메일은 보내지 않고 AccountMailSender 를 가짜로 바꿔 링크의 토큰을 가로챕니다.
 * 요청 제한(IP 마다 15분 5번)에 걸리지 않도록 요청마다 다른 IP 를 씁니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountRecoveryApiIntegrationTest {

    private static final String PASSWORD = "password1234!";
    private static final String NEW_PASSWORD = "newPassword5678!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AccountMailSender mailSender;

    private String signup() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@hama.com";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s", "name": "하마", "termsAgreed": true, "privacyAgreed": true, "ageConfirmed": true }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk());
        return email;
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset")
                .with(request -> {
                    request.setRemoteAddr(randomIp());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\" }".formatted(email)));
    }

    /** 재설정을 요청하고 메일로 나간 토큰을 돌려줍니다. */
    private String resetTokenFor(String email) throws Exception {
        requestReset(email).andExpect(status().isOk());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce()).sendPasswordReset(eq(email), token.capture());
        return token.getValue();
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"token\": \"%s\", \"newPassword\": \"%s\" }".formatted(token, newPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(randomIp());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password)));
    }

    private static String randomIp() {
        return "10.%d.%d.%d".formatted((int) (Math.random() * 255), (int) (Math.random() * 255),
                (int) (Math.random() * 255));
    }

    @Test
    void 링크로_비밀번호를_바꾸면_새_비밀번호로만_로그인되고_다른_기기는_로그아웃된다() throws Exception {
        String email = signup();   // 가입하면서 리프레시 토큰 1개가 생깁니다.
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        confirm(resetTokenFor(email), NEW_PASSWORD).andExpect(status().isOk());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        // 남은 건 방금 새 비밀번호로 로그인한 1개뿐입니다.
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getUserId().equals(userId))).hasSize(1);
    }

    @Test
    void 링크는_한_번만_쓸_수_있다() throws Exception {
        String token = resetTokenFor(signup());

        confirm(token, NEW_PASSWORD).andExpect(status().isOk());
        confirm(token, "another9999!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PASSWORD_RESET_TOKEN"));
    }

    @Test
    void 메일을_다시_받으면_옛_링크는_무효다() throws Exception {
        String email = signup();
        String oldToken = resetTokenFor(email);
        String newToken = resetTokenFor(email);
        assertThat(newToken).isNotEqualTo(oldToken);

        confirm(oldToken, NEW_PASSWORD).andExpect(status().isBadRequest());
        confirm(newToken, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 만료된_링크는_쓸_수_없다() throws Exception {
        String email = signup();
        String token = resetTokenFor(email);
        jdbcTemplate.update("""
                UPDATE password_reset_token SET expires_at = NOW() - INTERVAL 1 MINUTE
                WHERE user_id = (SELECT user_id FROM users WHERE email = ?)
                """, email);

        confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void 없는_이메일도_똑같이_성공하고_메일은_안_보낸다() throws Exception {
        String email = "nobody-" + UUID.randomUUID() + "@hama.com";

        requestReset(email).andExpect(status().isOk());

        verify(mailSender, never()).sendPasswordReset(eq(email), anyString());
    }

    @Test
    void 같은_이메일로_15분에_5번을_넘게_요청하면_429() throws Exception {
        String email = "nobody-" + UUID.randomUUID() + "@hama.com";
        for (int i = 0; i < 5; i++) {
            requestReset(email).andExpect(status().isOk());
        }
        requestReset(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void 새_비밀번호도_가입_규칙을_지켜야_한다() throws Exception {
        String token = resetTokenFor(signup());

        confirm(token, "short").andExpect(status().isBadRequest());
        // 규칙에 걸린 요청은 링크를 쓰지 않으므로 다시 시도할 수 있습니다.
        confirm(token, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 이메일_찾기_문의를_저장하고_운영자에게_알린다() throws Exception {
        String replyEmail = "reply-" + UUID.randomUUID() + "@hama.com";

        mockMvc.perform(post("/api/v1/auth/email-inquiry")
                        .with(request -> {
                            request.setRemoteAddr(randomIp());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"하마\", \"email\": \"%s\" }".formatted(replyEmail)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM email_inquiry WHERE reply_email = ?",
                String.class, replyEmail)).isEqualTo("하마");
        verify(mailSender).sendEmailInquiryNotice("하마", replyEmail);
    }
}
