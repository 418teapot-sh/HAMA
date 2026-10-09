package com.hama.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** 탈퇴 API 를 실제 필터 체인 + MySQL 로 확인합니다. 테스트끼리 섞이지 않도록 매번 새 이메일로 가입합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserWithdrawApiIntegrationTest {

    private static final Pattern REFRESH_COOKIE = Pattern.compile("refreshToken=([^;]*)");
    private static final String PASSWORD = "password1234";

    /** 사용자 데이터를 직접(user_id) 또는 목표·세션을 거쳐(goal_id, session_id) 들고 있는 테이블입니다. */
    private static final String USER_DATA_TABLES = """
            SELECT DISTINCT table_name FROM information_schema.columns
            WHERE table_schema = DATABASE() AND column_name IN ('user_id', 'goal_id', 'session_id')
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Session(Long userId, String email, String accessToken, String refreshToken) {
    }

    @Test
    void 탈퇴하면_데이터가_지워지고_쿠키가_삭제되며_로그인과_재발급이_막힌다() throws Exception {
        Session session = signup();
        createTodo(session);
        createGoal(session);
        assertThat(count("todo", session.userId())).isEqualTo(1);
        assertThat(count("goal", session.userId())).isEqualTo(1);

        mockMvc.perform(withdraw(session, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(userRepository.existsById(session.userId())).isFalse();
        for (String table : List.of("todo", "goal", "refresh_token")) {
            assertThat(count(table, session.userId())).as(table).isZero();
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(session.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호가_틀리면_400_이고_아무것도_지우지_않는다() throws Exception {
        Session session = signup();
        createTodo(session);

        mockMvc.perform(withdraw(session, "wrong-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));

        assertThat(userRepository.existsById(session.userId())).isTrue();
        assertThat(count("todo", session.userId())).isEqualTo(1);
    }

    @Test
    void 비밀번호를_안_보내면_400() throws Exception {
        Session session = signup();

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** 사용자 데이터 테이블을 새로 만들고 탈퇴 목록에 안 넣으면 여기서 실패합니다. payment 는 법정 보관이라 일부러 뺍니다. */
    @Test
    void 사용자_데이터_테이블은_모두_탈퇴_때_지운다() {
        String deletes = String.join("\n", UserService.DELETE_USER_DATA) + "\n";

        List<String> tables = jdbcTemplate.queryForList(USER_DATA_TABLES, String.class);
        assertThat(tables).contains("todo", "goal_checkin", "goal_ai_message");

        List<String> missing = tables.stream()
                .filter(table -> !table.equals("payment"))
                .filter(table -> !deletes.contains(" " + table + " "))
                .toList();

        assertThat(missing).isEmpty();
    }

    private Session signup() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@hama.com";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s", "name": "하마" }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = jsonMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asString();
        Matcher matcher = REFRESH_COOKIE.matcher(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(matcher.find()).isTrue();
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        return new Session(userId, email, accessToken, matcher.group(1));
    }

    private void createTodo(Session session) throws Exception {
        mockMvc.perform(post("/api/v1/todos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "category": "TASK", "content": "단어 외우기", "todoDate": "2026-10-10" }
                                """))
                .andExpect(status().is2xxSuccessful());
    }

    private void createGoal(Session session) throws Exception {
        mockMvc.perform(post("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "토익 850점", "startDate": "2026-10-10", "endDate": "2026-12-31" }
                                """))
                .andExpect(status().is2xxSuccessful());
    }

    private RequestBuilder withdraw(Session session, String password) {
        return delete("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "password": "%s" }
                        """.formatted(password));
    }

    private long count(String table, Long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
    }
}
