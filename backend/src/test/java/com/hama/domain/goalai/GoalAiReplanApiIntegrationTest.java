package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiReplanApiIntegrationTest extends GoalAiTestSupport {

    @Test
    void 밀린_미완료_AI_투두만_오늘_빈_시간으로_옮긴다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        List<Long> ids = jdbc.queryForList(
                "select todo_id from todo where goal_id = ? order by todo_date, todo_id limit 3", Long.class, goalId);
        // 10/3 투두를 9/30 으로 밀린 것처럼, 10/5 투두는 9/29 에 완료한 것처럼 만듭니다.
        jdbc.update("update todo set todo_date = '2026-09-30' where todo_id = ?", ids.get(1));
        jdbc.update("update todo set todo_date = '2026-09-29', status = 'COMPLETED' where todo_id = ?", ids.get(2));

        JsonNode data = call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", goalId), 200).get("data");

        assertThat(data.get("movedCount").asInt()).isEqualTo(1);
        assertThat(data.get("unplacedCount").asInt()).isZero();
        Map<String, Object> moved = jdbc.queryForMap(
                "select todo_date, start_time, postponed_count from todo where todo_id = ?", ids.get(1));
        assertThat(String.valueOf(moved.get("todo_date"))).isEqualTo("2026-10-01");
        assertThat(String.valueOf(moved.get("start_time"))).startsWith("10:00");
        assertThat(((Number) moved.get("postponed_count")).intValue()).isZero();
        assertThat(String.valueOf(jdbc.queryForObject("select start_time from todo where todo_id = ?",
                Object.class, ids.get(0)))).startsWith("09:00");
        assertThat(String.valueOf(jdbc.queryForObject("select todo_date from todo where todo_id = ?",
                Object.class, ids.get(2)))).isEqualTo("2026-09-29");
    }

    @Test
    void 진행_중이_아닌_목표는_409_기간_밖_fromDate_는_400_남의_목표는_403() throws Exception {
        User user = user();
        long planning = planningGoal(user);
        JsonNode error = call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", planning), 409);
        assertThat(error.at("/error/code").asString()).isEqualTo("GOAL_NOT_IN_PROGRESS");

        long planA = generatePlans(user, planning).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);

        JsonNode past = call(post("/api/v1/goals/ai/replan"), user,
                Map.of("goalId", planning, "fromDate", "2026-09-30"), 400);
        assertThat(past.at("/error/code").asString()).isEqualTo("AI_REPLAN_INVALID_DATE");
        call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", planning, "fromDate", "2027-01-01"), 400);
        call(post("/api/v1/goals/ai/replan"), user(), Map.of("goalId", planning), 403);
        call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", 999999999), 404);

        JsonNode none = call(post("/api/v1/goals/ai/replan"), user,
                Map.of("goalId", planning, "fromDate", "2026-10-02"), 200).get("data");
        assertThat(none.get("unplacedCount").asInt()).isZero();
    }
}
