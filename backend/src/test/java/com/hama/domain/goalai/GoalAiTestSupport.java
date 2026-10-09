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
        JsonNode response = call(post("/api/auth/signup"), null,
                Map.of("email", email, "password", "password1234!", "name", "목표AI", "termsAgreed", true, "privacyAgreed", true, "ageConfirmed", true), 200);
        return new User(users.findByEmail(email).orElseThrow().getId(), response.at("/data/accessToken").asString());
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

    protected static String question(String reply, String expects) {
        return """
                {"reply":"%s","expects":"%s","ready":false,"draft":null}
                """.formatted(reply, expects);
    }

    protected static final String READY_ANSWER = """
            {"reply":"정리해 봤어요. 확인해 주세요.","expects":null,"ready":true,
             "draft":{"title":"3개월 안에 토익 850점 달성","metricName":"토익 점수","unit":"점",
                      "startValue":700,"targetValue":850,"startDate":"2026-10-01","endDate":"2026-12-31",
                      "weeklyAvailableHours":10.0,"currentLevel":"LC 350 / RC 350"}}
            """;

    /** 10월(3회×60분)·11~12월(2회×120분) 플랜 A 와 매일 하는 플랜 B. A 는 투두 30개입니다. */
    protected static final String PLAN_ANSWER = """
            {"plans":[
              {"variant":"A","title":"여유형","summary":"주 3일, 하루 1시간.",
               "milestones":[{"title":"기초 다지기","startDate":"2026-10-01","endDate":"2026-10-31",
                              "weeklyTasks":[{"content":"LC 파트2 문제 풀이","sessionsPerWeek":3,"minutes":60}]},
                             {"title":"실전 연습","startDate":"2026-11-01","endDate":"2026-12-31",
                              "weeklyTasks":[{"content":"모의고사 1회","sessionsPerWeek":2,"minutes":120}]}]},
              {"variant":"B","title":"집중형","summary":"매일 1.5시간.",
               "milestones":[{"title":"몰입","startDate":"2026-10-01","endDate":"2026-12-31",
                              "weeklyTasks":[{"content":"RC 파트7 지문","sessionsPerWeek":7,"minutes":90}]}]}]}
            """;

    /** 대화 → 확정까지 거친 PLANNING 목표(2026-10-01 ~ 2026-12-31). */
    protected long planningGoal(User user) throws Exception {
        long sessionId = readySession(user);
        return call(post("/api/v1/goals/ai/sessions/" + sessionId + "/confirm"), user, null, 201)
                .at("/data/goalId").asLong();
    }

    protected JsonNode generatePlans(User user, long goalId) throws Exception {
        AI.answer(PLAN_ANSWER);
        return call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId), 201).get("data");
    }

    /** 세션을 만들고 답변 한 번으로 READY 까지 갑니다. */
    protected long readySession(User user) throws Exception {
        AI.answer(question("현재 토익 점수는 어느 정도인가요?", "CURRENT_LEVEL"));
        long sessionId = call(post("/api/v1/goals/ai/sessions"), user,
                Map.of("rawGoal", "3개월 안에 토익 850점 받고 싶어."), 201).at("/data/sessionId").asLong();
        AI.answer(READY_ANSWER);
        call(post("/api/v1/goals/ai/sessions/" + sessionId + "/messages"), user,
                Map.of("content", "지금 700점 정도예요."), 200);
        return sessionId;
    }
}
