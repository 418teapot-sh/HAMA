package com.hama.domain.goalai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.user.repository.UserRepository;
import com.hama.global.ai.AiClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** goals/ai 통합 테스트 공통. 오늘은 2026-10-01(KST, 목요일)로 고정하고 AI 는 미리 넣은 JSON 을 돌려줍니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class GoalAiTestSupport {

    protected static final FakeAiClient AI = new FakeAiClient();
    /** 가입하면 1회뿐이라 플랜 선택 → replan 을 이어 하면 부족합니다. 차감은 CreditDeductionIntegrationTest 가 따로 봅니다. */
    protected static final int DEFAULT_TEST_CREDITS = 10;

    @TestBean(methodName = "fakeAi")
    protected AiClient aiClient;

    @TestBean(name = "be3Clock", methodName = "fixedClock")
    protected Clock clock;

    static AiClient fakeAi() {
        return AI;
    }

    static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository users;

    protected record User(long id, String token) {
    }

    @BeforeEach
    void resetAi() {
        AI.reset();
    }

    protected User user() throws Exception {
        String email = "goalai-" + UUID.randomUUID() + "@hama.com";
        JsonNode response = call(post("/api/v1/auth/signup"), null,
                Map.of("email", email, "password", "password1234!", "name", "목표AI", "termsAgreed", true, "privacyAgreed", true, "ageConfirmed", true), 200);
        long id = users.findByEmail(email).orElseThrow().getId();
        jdbc.update("update users set credits = ? where user_id = ?", DEFAULT_TEST_CREDITS, id);
        return new User(id, response.at("/data/accessToken").asString());
    }

    protected JsonNode call(MockHttpServletRequestBuilder request, User user, Object body, int expected)
            throws Exception {
        if (user != null) {
            request.header("Authorization", "Bearer " + user.token());
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String text ? text : json.writeValueAsString(body));
        }
        String response = mvc.perform(request).andExpect(status().is(expected)).andReturn()
                .getResponse().getContentAsString();
        return json.readTree(response);
    }

    /**
     * 플랜 A(여유형)는 매일 1개(92일 × 60분 = totalTodos 92, avgDailyMinutes 60).
     * 플랜 B(집중형)는 매일 1개(90분) + 주 1회(120분) = totalTodos 106(92+14), avgDailyMinutes 108.
     */
    protected static final String PLAN_ANSWER = """
            {"plans":[
              {"variant":"A","title":"여유형","summary":"매일 영어 단어 30개씩 외우기 / 일주일에 모의고사 1번 보기",
               "tasks":[{"content":"영어 단어 30개 외우기","timesPerWeek":7,"minutes":60}]},
              {"variant":"B","title":"집중형","summary":"매일 영어 단어 50개씩 외우기 / 일주일에 모의고사 1번 보기",
               "tasks":[{"content":"영어 단어 50개 외우기","timesPerWeek":7,"minutes":90},
                        {"content":"모의고사 1회","timesPerWeek":1,"minutes":120}]}]}
            """;

    /** AI 없이 직접 만든 PLANNING 목표(2026-10-01 ~ 2026-12-31). */
    protected long planningGoal(User user) throws Exception {
        return call(post("/api/v1/goals"), user,
                Map.of("title", "3개월 안에 토익 850점", "currentStatus", "700점", "startDate", "2026-10-01",
                        "endDate", "2026-12-31"),
                201).at("/data/goalId").asLong();
    }

    protected JsonNode generatePlans(User user, long goalId) throws Exception {
        AI.answer(PLAN_ANSWER);
        return call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId), 201).get("data");
    }
}
