package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiPlanApiIntegrationTest extends GoalAiTestSupport {

    @Test
    void PLANNING_목표에_A_B_플랜을_만든다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);

        JsonNode plans = generatePlans(user, goalId).get("plans");

        assertThat(plans).hasSize(2);
        JsonNode a = plans.get(0);
        assertThat(a.get("planId").asLong()).isPositive();
        assertThat(a.get("variant").asString()).isEqualTo("A");
        assertThat(a.get("title").asString()).isEqualTo("여유형");
        assertThat(a.get("summary").asString()).isEqualTo("주 3일, 하루 1시간.");
        assertThat(a.get("milestones")).hasSize(2);
        assertThat(a.at("/milestones/0/seq").asInt()).isEqualTo(1);
        assertThat(a.at("/milestones/0/startDate").asString()).isEqualTo("2026-10-01");
        assertThat(a.at("/milestones/1/endDate").asString()).isEqualTo("2026-12-31");
        assertThat(a.at("/stats/totalTodos").asInt()).isEqualTo(30);
        assertThat(a.at("/stats/avgDailyMinutes").asInt()).isEqualTo(94);
        assertThat(plans.get(1).get("variant").asString()).isEqualTo("B");
        assertThat(plans.get(1).at("/stats/totalTodos").asInt()).isEqualTo(92);
        assertThat(AI.requests().getLast().purpose()).isEqualTo("goal-plan");
        assertThat(AI.requests().getLast().messages().getFirst().content()).contains("\"planStartDate\":\"2026-10-01\"");
    }

    @Test
    void 조회는_selected_를_포함하고_다시_만들면_기존_플랜을_바꾼다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long first = generatePlans(user, goalId).at("/plans/0/planId").asLong();

        JsonNode listed = call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .at("/data/plans");
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).get("planId").asLong()).isEqualTo(first);
        assertThat(listed.get(0).get("selected").asBoolean()).isFalse();
        assertThat(listed.at("/0/milestones/0/title").asString()).isEqualTo("기초 다지기");

        long second = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        JsonNode replaced = call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null,
                200).at("/data/plans");
        assertThat(replaced).hasSize(2);
        assertThat(replaced.get(0).get("planId").asLong()).isEqualTo(second).isNotEqualTo(first);
    }

    @Test
    void preference_를_프롬프트에_반영한다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        AI.answer(PLAN_ANSWER);

        call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId, "preference", "INTENSIVE"), 201);

        assertThat(AI.requests().getLast().systemPrompt()).contains("짧고 굵게");
        call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId, "preference", "FAST"), 400);
    }

    @Test
    void 플랜을_펼칠_수_없는_AI_응답은_502() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        AI.answer("""
                {"plans":[{"variant":"A","title":"여유형","summary":"-","milestones":[]}]}
                """);

        JsonNode error = call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId), 502);

        assertThat(error.at("/error/code").asString()).isEqualTo("AI_UPSTREAM_ERROR");
        assertThat(call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .at("/data/plans")).isEmpty();
    }

    @Test
    void 진행_중인_목표는_409_남의_목표는_403_없는_목표는_404() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        jdbc.update("update goal set status = 'IN_PROGRESS' where goal_id = ?", goalId);

        JsonNode error = call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId), 409);
        assertThat(error.at("/error/code").asString()).isEqualTo("GOAL_NOT_PLANNING");

        long other = planningGoal(user);
        call(post("/api/v1/goals/ai/plans"), user(), Map.of("goalId", other), 403);
        call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(other)), user(), null, 403);
        call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", 999999999), 404);
        call(post("/api/v1/goals/ai/plans"), user, Map.of(), 400);
        assertThat(AI.requests().stream().filter(r -> r.purpose().equals("goal-plan"))).isEmpty();
    }
}
