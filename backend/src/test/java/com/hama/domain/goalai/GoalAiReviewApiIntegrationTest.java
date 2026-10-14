package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiReviewApiIntegrationTest extends GoalAiTestSupport {

    private static final String ACHIEVED_ANSWER = """
            {"achieved":true,"comparison":"700점에서 860점으로, 목표 850점을 넘었어요.",
             "message":"목표를 달성했어요! 정말 잘하셨어요."}
            """;
    private static final String MISSED_ANSWER = """
            {"achieved":false,"comparison":"700점에서 780점으로, 목표 850점에는 못 미쳤어요.",
             "message":"그래도 80점이나 올랐어요. 다음엔 더 잘할 수 있어요."}
            """;

    /** 플랜을 골라 IN_PROGRESS 로 만든 목표. */
    private long inProgressGoal(User user) throws Exception {
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        return goalId;
    }

    private void checkin(User user, long goalId, String type, Object value, Boolean achieved) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("goalId", goalId, "type", type, "value", value));
        if (achieved != null) {
            body.put("achieved", achieved);
        }
        call(post("/api/v1/todos/state/checkins"), user, body, 201);
    }

    @Test
    void END_체크인_뒤_리뷰를_만들면_AI_가_판정한_결과를_저장한다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        checkin(user, goalId, "START", 700, null);
        checkin(user, goalId, "MID", 780, null);
        checkin(user, goalId, "END", 860, true);

        AI.answer(ACHIEVED_ANSWER);
        JsonNode data = call(post("/api/v1/goals/ai/reviews"), user,
                Map.of("goalId", goalId, "currentStatus", "토익 860점 획득"), 201).get("data");

        assertThat(data.get("goalId").asLong()).isEqualTo(goalId);
        assertThat(data.get("achieved").asBoolean()).isTrue();
        assertThat(data.get("comparison").asString()).contains("860");
        assertThat(data.get("message").asString()).isNotBlank();
        assertThat(data.get("currentStatus").asString()).isEqualTo("토익 860점 획득");
        assertThat(data.get("resultStatus").asString()).isEqualTo("ACHIEVED");

        assertThat(jdbc.queryForObject("select result_status from goal where goal_id = ?", String.class, goalId))
                .isEqualTo("ACHIEVED");
        assertThat(jdbc.queryForObject("select count(*) from goal_review where goal_id = ?", Integer.class, goalId))
                .isEqualTo(1);

        assertThat(AI.requests().getLast().purpose()).isEqualTo("goal-review");
        assertThat(AI.requests().getLast().messages().getFirst().content())
                .contains("토익 860점 획득").contains("\"progressRate\"");
    }

    @Test
    void 다시_리뷰하면_기존_행을_덮어쓴다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        checkin(user, goalId, "END", 860, true);

        AI.answer(ACHIEVED_ANSWER);
        call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 201);

        AI.answer(MISSED_ANSWER);
        JsonNode data = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 201).get("data");

        assertThat(data.get("achieved").asBoolean()).isFalse();
        assertThat(data.get("resultStatus").asString()).isEqualTo("MISSED");
        assertThat(jdbc.queryForObject("select count(*) from goal_review where goal_id = ?", Integer.class, goalId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select result_status from goal where goal_id = ?", String.class, goalId))
                .isEqualTo("MISSED");
    }

    @Test
    void 계획_중이거나_END_체크인이_없으면_409_남의_목표는_403_없는_목표는_404() throws Exception {
        User user = user();
        long planning = planningGoal(user);
        JsonNode notStarted = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", planning), 409);
        assertThat(notStarted.at("/error/code").asString()).isEqualTo("AI_REVIEW_GOAL_NOT_STARTED");

        long planA = generatePlans(user, planning).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        JsonNode noEnd = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", planning), 409);
        assertThat(noEnd.at("/error/code").asString()).isEqualTo("AI_REVIEW_END_REQUIRED");

        checkin(user, planning, "START", 700, null);
        JsonNode stillNoEnd = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", planning), 409);
        assertThat(stillNoEnd.at("/error/code").asString()).isEqualTo("AI_REVIEW_END_REQUIRED");

        call(post("/api/v1/goals/ai/reviews"), user(), Map.of("goalId", planning), 403);
        call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", 999999999), 404);
    }

    @Test
    void 리뷰_조회는_만든_결과를_돌려주고_없으면_404() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        checkin(user, goalId, "END", 860, true);

        JsonNode missing = call(get("/api/v1/goals/ai/reviews").param("goalId", String.valueOf(goalId)), user, null,
                404);
        assertThat(missing.at("/error/code").asString()).isEqualTo("AI_REVIEW_NOT_FOUND");

        AI.answer(ACHIEVED_ANSWER);
        call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 201);

        JsonNode data = call(get("/api/v1/goals/ai/reviews").param("goalId", String.valueOf(goalId)), user, null,
                200).get("data");
        assertThat(data.get("achieved").asBoolean()).isTrue();
        assertThat(data.get("resultStatus").asString()).isEqualTo("ACHIEVED");

        call(get("/api/v1/goals/ai/reviews").param("goalId", String.valueOf(goalId)), user(), null, 403);
    }
}
