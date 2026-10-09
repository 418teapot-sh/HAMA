package com.hama.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.auth.service.LoginAttemptLimiter;
import com.hama.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    /** {@code DELETE FROM todo ...} 와 {@code DELETE m FROM milestone m JOIN ...} 에서 지우는 테이블 이름입니다. */
    private static final Pattern DELETE_TARGET = Pattern.compile("^DELETE (?:\\w+ )?FROM (\\w+)");

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

    private record Seeded(Long goalId, Long sessionId) {
    }

    @Test
    void 탈퇴하면_데이터가_지워지고_쿠키가_삭제되며_로그인과_재발급이_막힌다() throws Exception {
        Session session = signup();
        Seeded seeded = seedAll(session);
        assertThat(remainingRows(session, seeded)).allSatisfy((table, rows) -> assertThat(rows).as(table).isOne());

        mockMvc.perform(withdraw(session, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(userRepository.existsById(session.userId())).isFalse();
        assertThat(remainingRows(session, seeded)).allSatisfy((table, rows) -> assertThat(rows).as(table).isZero());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(session.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    /** JOIN 으로 지우는 문장이 조건을 잘못 걸면 다른 사람 데이터까지 지울 수 있어서 확인합니다. */
    @Test
    void 다른_사용자의_데이터는_지우지_않는다() throws Exception {
        Session leaving = signup();
        seedAll(leaving);
        Session staying = signup();
        Seeded stayingRows = seedAll(staying);

        mockMvc.perform(withdraw(leaving, PASSWORD)).andExpect(status().isOk());

        assertThat(remainingRows(staying, stayingRows)).allSatisfy((table, rows) -> assertThat(rows).as(table).isOne());
    }

    @Test
    void 비밀번호가_틀리면_400_이고_아무것도_지우지_않는다() throws Exception {
        Session session = signup();
        createTodo(session);

        mockMvc.perform(withdraw(session, "wrong-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));

        assertThat(userRepository.existsById(session.userId())).isTrue();
        assertThat(count("SELECT COUNT(*) FROM todo WHERE user_id = ?", session.userId())).isOne();
    }

    @Test
    void 비밀번호를_5번_틀리면_맞는_비밀번호도_429() throws Exception {
        Session session = signup();
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS; i++) {
            mockMvc.perform(withdraw(session, "wrong-password")).andExpect(status().isBadRequest());
        }

        mockMvc.perform(withdraw(session, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_PASSWORD_ATTEMPTS"));
        assertThat(userRepository.existsById(session.userId())).isTrue();
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
        // JOIN 에만 나오는 테이블(goal, goal_ai_session)을 지운 것으로 세지 않도록 DELETE 대상만 모읍니다.
        Set<String> deleted = UserService.DELETE_USER_DATA.stream()
                .map(DELETE_TARGET::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toSet());

        List<String> tables = jdbcTemplate.queryForList(USER_DATA_TABLES, String.class);
        assertThat(tables).contains("todo", "goal_checkin", "goal_ai_message");

        List<String> missing = tables.stream()
                .filter(table -> !table.equals("payment"))
                .filter(table -> !deleted.contains(table))
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

    /** 탈퇴 때 지우는 테이블마다 한 행씩 넣습니다. 목표 아래 테이블은 AI 호출 없이 채우려고 직접 넣습니다. */
    private Seeded seedAll(Session session) throws Exception {
        createTodo(session);
        createGoal(session);
        Long goalId = jdbcTemplate.queryForObject("SELECT goal_id FROM goal WHERE user_id = ?", Long.class,
                session.userId());
        Long milestoneId = insert("""
                INSERT INTO milestone (goal_id, seq, title, start_date, end_date, status, created_at, updated_at)
                VALUES (?, 1, '1단계', '2026-10-10', '2026-10-31', 'PENDING', NOW(6), NOW(6))""", goalId);
        insert("""
                INSERT INTO period_goal (goal_id, milestone_id, period_type, seq, title, start_date, end_date, status,
                                         created_at, updated_at)
                VALUES (?, ?, 'WEEKLY', 1, '1주차', '2026-10-10', '2026-10-16', 'PENDING', NOW(6), NOW(6))""",
                goalId, milestoneId);
        insert("""
                INSERT INTO goal_checkin (goal_id, type, checked_at, created_at, updated_at)
                VALUES (?, 'START', NOW(6), NOW(6), NOW(6))""", goalId);
        insert("""
                INSERT INTO goal_plan (goal_id, variant, title, summary, preference, detail_json, total_todos,
                                       avg_daily_minutes, selected, created_at, updated_at)
                VALUES (?, 'A', '여유형', '주 3일', 'BALANCED', '{}', 0, 0, 0, NOW(6), NOW(6))""", goalId);
        Long sessionId = insert("""
                INSERT INTO goal_ai_session (user_id, raw_goal, status, created_at, updated_at)
                VALUES (?, '토익 850점', 'COLLECTING', NOW(6), NOW(6))""", session.userId());
        insert("""
                INSERT INTO goal_ai_message (session_id, role, content, created_at, updated_at)
                VALUES (?, 'USER', '토익 850점', NOW(6), NOW(6))""", sessionId);
        return new Seeded(goalId, sessionId);
    }

    private Long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 목표·세션이 지워진 뒤에도 셀 수 있도록 자식 테이블은 미리 받아 둔 goal_id·session_id 로 셉니다. */
    private Map<String, Long> remainingRows(Session session, Seeded seeded) {
        Map<String, Long> rows = new LinkedHashMap<>();
        for (String table : List.of("todo", "goal", "goal_ai_session", "refresh_token")) {
            rows.put(table, count("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", session.userId()));
        }
        for (String table : List.of("milestone", "period_goal", "goal_checkin", "goal_plan")) {
            rows.put(table, count("SELECT COUNT(*) FROM " + table + " WHERE goal_id = ?", seeded.goalId()));
        }
        rows.put("goal_ai_message", count("SELECT COUNT(*) FROM goal_ai_message WHERE session_id = ?",
                seeded.sessionId()));
        return rows;
    }

    private long count(String sql, Long id) {
        return jdbcTemplate.queryForObject(sql, Long.class, id);
    }
}
