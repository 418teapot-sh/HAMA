package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiPlanApiIntegrationTest extends GoalAiTestSupport {

    @Test
    void 가능한_날이_없으면_AI와_생성횟수를_사용하지_않는다() throws Exception {
        User user = user();
        long goalId = call(post("/api/v1/goals"), user,
                Map.of("title", "하루 목표", "currentStatus", "시작", "startDate", "2026-10-01", "endDate", "2026-10-01",
                        "fixedSchedules", List.of(Map.of("dayOfWeek", "THURSDAY", "content", "바쁨"))), 201)
                .at("/data/goalId").asLong();
        int calls = AI.requests().size();
        JsonNode error = call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId), 409);
        assertThat(error.at("/error/code").asString()).isEqualTo("AI_PLAN_NO_AVAILABLE_DAY");
        assertThat(AI.requests()).hasSize(calls);
        assertThat(call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .at("/data/remainingGenerations").asInt()).isEqualTo(3);
    }

    @Test
    void 기존_미선택_플랜은_무료_재계산하고_선택한_통계와_생성수가_같다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planId = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        jdbc.update("insert into goal_fixed_schedule(goal_id, day_of_week, content) values (?, 'MONDAY', '바쁨')", goalId);
        String before = jdbc.queryForObject("select detail_json from goal_plan where plan_id=?", String.class, planId);
        int calls = AI.requests().size();
        JsonNode listed = call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200);
        int expected = listed.at("/data/plans/0/stats/totalTodos").asInt();
        assertThat(expected).isEqualTo(79);
        assertThat(jdbc.queryForObject("select detail_json from goal_plan where plan_id=?", String.class, planId)).isEqualTo(before);
        assertThat(AI.requests()).hasSize(calls);
        JsonNode selected = call(post("/api/v1/goals/ai/plans/" + planId + "/select"), user, null, 200);
        assertThat(selected.at("/data/created/todos").asInt()).isEqualTo(expected);
        assertThat(call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .at("/data/plans/0/stats/totalTodos").asInt()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from todo where goal_id=? and dayofweek(todo_date)=2", Integer.class, goalId)).isZero();
    }

    @Test
    void 기존_플랜의_가능한_날이_없으면_조회는_0이고_선택은_차감없이_실패한다() throws Exception {
        User user = user();
        long goalId = call(post("/api/v1/goals"), user, Map.of("title", "하루 목표", "currentStatus", "시작",
                "startDate", "2026-10-01", "endDate", "2026-10-01"), 201).at("/data/goalId").asLong();
        long planId = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        jdbc.update("insert into goal_fixed_schedule(goal_id, day_of_week, content) values (?, 'THURSDAY', '바쁨')", goalId);
        int credits = jdbc.queryForObject("select credits from users where user_id=?", Integer.class, user.id());
        assertThat(call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .at("/data/plans/0/stats/totalTodos").asInt()).isZero();
        assertThat(call(post("/api/v1/goals/ai/plans/" + planId + "/select"), user, null, 409)
                .at("/error/code").asString()).isEqualTo("AI_PLAN_NO_AVAILABLE_DAY");
        assertThat(jdbc.queryForObject("select credits from users where user_id=?", Integer.class, user.id())).isEqualTo(credits);
        assertThat(jdbc.queryForObject("select count(*) from todo where goal_id=?", Integer.class, goalId)).isZero();
    }

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
        assertThat(a.get("tasks")).hasSize(1);
        assertThat(a.at("/tasks/0/content").asString()).isEqualTo("영어 단어 30개 외우기");
        assertThat(a.at("/tasks/0/timesPerWeek").asInt()).isEqualTo(7);
        assertThat(a.at("/stats/totalTodos").asInt()).isEqualTo(92);
        assertThat(a.at("/stats/avgDailyMinutes").asInt()).isEqualTo(60);
        assertThat(plans.get(1).get("variant").asString()).isEqualTo("B");
        assertThat(plans.get(1).get("tasks")).hasSize(2);
        assertThat(plans.get(1).at("/stats/totalTodos").asInt()).isEqualTo(106);
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
        assertThat(listed.at("/0/tasks/0/content").asString()).isEqualTo("영어 단어 30개 외우기");

        long second = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        JsonNode replaced = call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null,
                200).at("/data/plans");
        assertThat(replaced).hasSize(2);
        assertThat(replaced.get(0).get("planId").asLong()).isEqualTo(second).isNotEqualTo(first);
    }

    @Test
    void 플랜은_목표당_3번까지_만들고_4번째는_AI_를_부르지_않고_429() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        assertThat(call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .at("/data/remainingGenerations").asInt()).isEqualTo(3);

        assertThat(generatePlans(user, goalId).get("remainingGenerations").asInt()).isEqualTo(2);
        assertThat(generatePlans(user, goalId).get("remainingGenerations").asInt()).isEqualTo(1);
        assertThat(generatePlans(user, goalId).get("remainingGenerations").asInt()).isZero();
        long aiCalls = AI.requests().size();

        AI.answer(PLAN_ANSWER);
        JsonNode error = call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId), 429);

        assertThat(error.at("/error/code").asString()).isEqualTo("AI_PLAN_GENERATION_LIMIT");
        assertThat(AI.requests()).hasSize((int) aiCalls);
        JsonNode listed = call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .get("data");
        assertThat(listed.get("remainingGenerations").asInt()).isZero();
        assertThat(listed.get("plans")).hasSize(2);
    }

    @Test
    void 실패한_생성은_횟수에_넣지_않는다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        AI.answer("""
                {"plans":[{"variant":"A","title":"여유형","summary":"-","tasks":[]}]}
                """);
        call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goalId), 502);

        assertThat(generatePlans(user, goalId).get("remainingGenerations").asInt()).isEqualTo(2);
    }

    @Test
    void 고정_일정_요일에는_투두를_두지_않는다() throws Exception {
        User user = user();
        long goalId = call(post("/api/v1/goals"), user,
                Map.of("title", "3개월 안에 토익 850점", "currentStatus", "700점", "startDate", "2026-10-01",
                        "endDate", "2026-12-31", "fixedSchedules",
                        List.of(Map.of("dayOfWeek", "MONDAY", "content", "야근"))),
                201).at("/data/goalId").asLong();

        JsonNode plans = generatePlans(user, goalId).get("plans");

        // 92일 중 월요일 13일을 빼면 79일. 매일 하는 할 일도 월요일은 건너뜁니다.
        assertThat(plans.at("/0/stats/totalTodos").asInt()).isEqualTo(79);
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
                {"plans":[{"variant":"A","title":"여유형","summary":"-","tasks":[]}]}
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
