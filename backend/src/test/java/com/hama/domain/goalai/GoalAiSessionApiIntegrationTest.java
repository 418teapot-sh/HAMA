package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hama.global.ai.AiMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiSessionApiIntegrationTest extends GoalAiTestSupport {

    @Test
    void 세션을_시작하면_AI_의_첫_질문을_돌려준다() throws Exception {
        User user = user();
        AI.answer(question("현재 토익 점수(또는 수준)는 어느 정도인가요?", "CURRENT_LEVEL"));

        JsonNode data = call(post("/api/v1/goals/ai/sessions"), user,
                Map.of("rawGoal", "3개월 안에 토익 850점 받고 싶어."), 201).get("data");

        assertThat(data.get("sessionId").asLong()).isPositive();
        assertThat(data.get("status").asString()).isEqualTo("COLLECTING");
        assertThat(data.at("/aiMessage/content").asString()).isEqualTo("현재 토익 점수(또는 수준)는 어느 정도인가요?");
        assertThat(data.at("/aiMessage/expects").asString()).isEqualTo("CURRENT_LEVEL");
        assertThat(AI.requests().getFirst().systemPrompt()).contains("2026-10-01");
    }

    @Test
    void 답변으로_정보가_모이면_READY_와_초안을_돌려주고_대화_이력을_보낸다() throws Exception {
        User user = user();
        AI.answer(question("현재 토익 점수는?", "CURRENT_LEVEL"));
        long sessionId = call(post("/api/v1/goals/ai/sessions"), user,
                Map.of("rawGoal", "토익 850"), 201).at("/data/sessionId").asLong();

        AI.answer(question("주에 몇 시간 공부할 수 있나요?", "AVAILABLE_TIME"));
        JsonNode collecting = call(post("/api/v1/goals/ai/sessions/" + sessionId + "/messages"), user,
                Map.of("content", "700점이요"), 200).get("data");
        assertThat(collecting.get("status").asString()).isEqualTo("COLLECTING");
        assertThat(collecting.get("goalDraft").isNull()).isTrue();

        AI.answer(READY_ANSWER);
        JsonNode ready = call(post("/api/v1/goals/ai/sessions/" + sessionId + "/messages"), user,
                Map.of("content", "주 10시간이요"), 200).get("data");
        assertThat(ready.get("status").asString()).isEqualTo("READY");
        assertThat(ready.at("/aiMessage/expects").isNull()).isTrue();
        assertThat(ready.at("/goalDraft/title").asString()).isEqualTo("3개월 안에 토익 850점 달성");
        assertThat(ready.at("/goalDraft/endDate").asString()).isEqualTo("2026-12-31");
        assertThat(ready.at("/goalDraft/weeklyAvailableHours").asDouble()).isEqualTo(10.0);

        var history = AI.requests().getLast().messages();
        assertThat(history).hasSize(5);
        assertThat(history.getFirst()).isEqualTo(AiMessage.user("토익 850"));
        assertThat(history.get(1).role()).isEqualTo(AiMessage.Role.ASSISTANT);
        assertThat(history.getLast()).isEqualTo(AiMessage.user("주 10시간이요"));
    }

    @Test
    void 종료일이_과거인_초안은_READY_로_받지_않는다() throws Exception {
        User user = user();
        AI.answer(READY_ANSWER.replace("2026-12-31", "2026-09-01"));

        JsonNode data = call(post("/api/v1/goals/ai/sessions"), user, Map.of("rawGoal", "토익"), 201).get("data");

        assertThat(data.get("status").asString()).isEqualTo("COLLECTING");
    }

    @Test
    void 세션_조회는_대화와_초안을_보여주고_남의_세션은_403_없는_세션은_404() throws Exception {
        User owner = user();
        long sessionId = readySession(owner);

        JsonNode data = call(get("/api/v1/goals/ai/sessions/" + sessionId), owner, null, 200).get("data");
        assertThat(data.get("status").asString()).isEqualTo("READY");
        assertThat(data.get("rawGoal").asString()).isEqualTo("3개월 안에 토익 850점 받고 싶어.");
        assertThat(data.get("messages")).hasSize(4);
        assertThat(data.at("/messages/0/role").asString()).isEqualTo("USER");
        assertThat(data.at("/messages/1/role").asString()).isEqualTo("AI");
        assertThat(data.at("/messages/0/createdAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(data.at("/goalDraft/targetValue").asDouble()).isEqualTo(850.0);
        assertThat(data.get("realityResult").isNull()).isTrue();

        call(get("/api/v1/goals/ai/sessions/" + sessionId), user(), null, 403);
        call(get("/api/v1/goals/ai/sessions/999999999"), owner, null, 404);
        call(post("/api/v1/goals/ai/sessions/" + sessionId + "/messages"), user(), Map.of("content", "탈취"), 403);
    }

    @Test
    void 입력_검증과_대화_횟수_상한() throws Exception {
        User user = user();
        call(post("/api/v1/goals/ai/sessions"), user, Map.of("rawGoal", " "), 400);
        call(post("/api/v1/goals/ai/sessions"), user, Map.of("rawGoal", "가".repeat(501)), 400);

        AI.answer(question("현재 수준은?", "CURRENT_LEVEL"));
        long sessionId = call(post("/api/v1/goals/ai/sessions"), user, Map.of("rawGoal", "토익"), 201)
                .at("/data/sessionId").asLong();
        for (int i = 1; i < 10; i++) {
            AI.answer(question("조금 더 알려주세요.", "OTHER"));
            call(post("/api/v1/goals/ai/sessions/" + sessionId + "/messages"), user, Map.of("content", "답 " + i), 200);
        }
        JsonNode error = call(post("/api/v1/goals/ai/sessions/" + sessionId + "/messages"), user,
                Map.of("content", "한 번 더"), 409);
        assertThat(error.at("/error/code").asString()).isEqualTo("AI_SESSION_TURN_LIMIT");
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        call(post("/api/v1/goals/ai/sessions"), null, Map.of("rawGoal", "토익"), 401);
        call(get("/api/v1/goals/ai/sessions/1"), null, null, 401);
    }
}
