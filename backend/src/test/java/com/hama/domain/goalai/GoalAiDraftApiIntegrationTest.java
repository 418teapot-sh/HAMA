package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiDraftApiIntegrationTest extends GoalAiTestSupport {

    private static final String REALITY_ANSWER = """
            {"verdict":"CHALLENGING","comment":"현재 수준에서 150점 상승은 가능하지만 빠듯합니다.",
             "requiredHours":180,"needsSplit":false,
             "suggestions":[{"type":"EXTEND_PERIOD","label":"기간을 1월 말까지 늘리기","patch":{"endDate":"2027-01-31"}},
                            {"type":"MORE_TIME","label":"주 14시간으로 늘리기","patch":{"weeklyAvailableHours":14}}]}
            """;

    @Test
    void 현실성_체크는_서버가_가용시간을_계산하고_세션에_남긴다() throws Exception {
        User user = user();
        long sessionId = readySession(user);
        AI.answer(REALITY_ANSWER);

        JsonNode data = call(post(path(sessionId, "/reality-check")), user, null, 200).get("data");

        assertThat(data.get("verdict").asString()).isEqualTo("CHALLENGING");
        assertThat(data.at("/comparison/requiredHours").asInt()).isEqualTo(180);
        assertThat(data.at("/comparison/availableHours").asInt()).isEqualTo(131);
        assertThat(data.at("/comparison/gapHours").asInt()).isEqualTo(49);
        assertThat(data.get("needsSplit").asBoolean()).isFalse();
        assertThat(data.at("/suggestions/0/patch/endDate").asString()).isEqualTo("2027-01-31");
        assertThat(AI.requests().getLast().purpose()).isEqualTo("reality-check");

        JsonNode detail = call(get(path(sessionId, "")), user, null, 200).get("data");
        assertThat(detail.at("/realityResult/verdict").asString()).isEqualTo("CHALLENGING");
    }

    @Test
    void 초안을_수정하면_보낸_필드만_바뀌고_현실성_결과는_지워진다() throws Exception {
        User user = user();
        long sessionId = readySession(user);
        AI.answer(REALITY_ANSWER);
        call(post(path(sessionId, "/reality-check")), user, null, 200);

        JsonNode draft = call(patch(path(sessionId, "/draft")), user,
                Map.of("endDate", "2027-01-31", "weeklyAvailableHours", 14), 200).at("/data/goalDraft");

        assertThat(draft.get("endDate").asString()).isEqualTo("2027-01-31");
        assertThat(draft.get("weeklyAvailableHours").asDouble()).isEqualTo(14.0);
        assertThat(draft.get("title").asString()).isEqualTo("3개월 안에 토익 850점 달성");
        assertThat(draft.get("targetValue").asDouble()).isEqualTo(850.0);
        assertThat(call(get(path(sessionId, "")), user, null, 200).at("/data/realityResult").isNull()).isTrue();

        JsonNode cleared = call(patch(path(sessionId, "/draft")), user, "{\"currentLevel\":null}", 200)
                .at("/data/goalDraft");
        assertThat(cleared.get("currentLevel").isNull()).isTrue();
        assertThat(cleared.get("endDate").asString()).isEqualTo("2027-01-31");
    }

    @Test
    void 잘못된_초안_수정은_400() throws Exception {
        User user = user();
        long sessionId = readySession(user);

        call(patch(path(sessionId, "/draft")), user, Map.of("endDate", "2026-09-01"), 400);
        call(patch(path(sessionId, "/draft")), user, Map.of("weeklyAvailableHours", 200), 400);
        call(patch(path(sessionId, "/draft")), user, Map.of("title", " "), 400);
    }

    @Test
    void 확정하면_PLANNING_목표가_생기고_세션은_더_진행할_수_없다() throws Exception {
        User user = user();
        long sessionId = readySession(user);
        AI.answer(REALITY_ANSWER);
        call(post(path(sessionId, "/reality-check")), user, null, 200);

        JsonNode data = call(post(path(sessionId, "/confirm")), user, null, 201).get("data");
        long goalId = data.get("goalId").asLong();
        assertThat(data.get("status").asString()).isEqualTo("PLANNING");

        Map<String, Object> goal = jdbc.queryForMap("select * from goal where goal_id = ?", goalId);
        assertThat(goal.get("user_id")).isEqualTo(user.id());
        assertThat(goal.get("title")).isEqualTo("3개월 안에 토익 850점 달성");
        assertThat(goal.get("status")).isEqualTo("PLANNING");
        assertThat(goal.get("current_level")).isEqualTo("LC 350 / RC 350");
        assertThat(goal.get("reality_verdict")).isEqualTo("CHALLENGING");

        JsonNode detail = call(get(path(sessionId, "")), user, null, 200).get("data");
        assertThat(detail.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(detail.get("goalId").asLong()).isEqualTo(goalId);

        call(post(path(sessionId, "/confirm")), user, null, 409);
        call(patch(path(sessionId, "/draft")), user, Map.of("title", "새 제목"), 409);
        call(post(path(sessionId, "/messages")), user, Map.of("content", "더 얘기"), 409);
    }

    @Test
    void 초안이_없는_세션은_409_남의_세션은_403() throws Exception {
        User user = user();
        AI.answer(question("현재 수준은?", "CURRENT_LEVEL"));
        long collecting = call(post("/api/v1/goals/ai/sessions"), user, Map.of("rawGoal", "토익"), 201)
                .at("/data/sessionId").asLong();

        JsonNode error = call(post(path(collecting, "/reality-check")), user, null, 409);
        assertThat(error.at("/error/code").asString()).isEqualTo("AI_SESSION_NOT_READY");
        call(post(path(collecting, "/confirm")), user, null, 409);
        call(patch(path(collecting, "/draft")), user, Map.of("title", "새 제목"), 409);

        long ready = readySession(user);
        User other = user();
        call(post(path(ready, "/reality-check")), other, null, 403);
        call(patch(path(ready, "/draft")), other, Map.of("title", "탈취"), 403);
        call(post(path(ready, "/confirm")), other, null, 403);
    }

    private static String path(long sessionId, String suffix) {
        return "/api/v1/goals/ai/sessions/" + sessionId + suffix;
    }
}
