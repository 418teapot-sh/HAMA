package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiSelectApiIntegrationTest extends GoalAiTestSupport {

    @Test
    void 플랜을_고르면_계층을_만들고_투두를_일정_밖_빈_시간에_둔다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        // 평일 09~18 회사(반복), 10/3(토) 종일 개인 일정, 10/1 18:00~18:30 기존 투두
        schedule(user, "FIXED", "회사", "2026-10-01T09:00:00", "2026-10-01T18:00:00", false,
                "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");
        schedule(user, "PERSONAL", "여행", "2026-10-03T00:00:00", "2026-10-04T00:00:00", true, null);
        call(post("/api/v1/todos"), user, Map.of("category", "TASK", "content", "장보기", "todoDate", "2026-10-01",
                "startTime", "18:00", "endTime", "18:30"), 201);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();

        JsonNode data = call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200).get("data");

        assertThat(data.get("goalId").asLong()).isEqualTo(goalId);
        assertThat(data.get("status").asString()).isEqualTo("IN_PROGRESS");
        // 플랜 A 는 매일 1개(92일). 10/3(토)는 종일 개인 일정이라 그 하루만 빈 시간이 없습니다.
        assertThat(data.at("/created/todos").asInt()).isEqualTo(92);
        assertThat(data.at("/placement/placed").asInt()).isEqualTo(91);
        assertThat(data.at("/placement/unplaced").asInt()).isEqualTo(1);
        assertThat(data.at("/placement/unplacedTodos/0/content").asString()).isEqualTo("영어 단어 30개 외우기");
        assertThat(data.at("/placement/unplacedTodos/0/todoDate").asString()).isEqualTo("2026-10-03");

        List<Map<String, Object>> todos = jdbc.queryForList("""
                select todo_date, start_time, end_time from todo
                where goal_id = ? and category = 'AI_GOAL_TASK' and deleted_at is null
                order by todo_date, todo_id limit 3
                """, goalId);
        assertThat(todos).extracting(row -> String.valueOf(row.get("todo_date")))
                .containsExactly("2026-10-01", "2026-10-02", "2026-10-03");
        // 10/1(목)은 회사(09~18) + 기존 투두(18~18:30) 다음 빈 시간, 10/2(금)은 회사(09~18) 다음 빈 시간.
        assertThat(String.valueOf(todos.get(0).get("start_time"))).startsWith("18:30");
        assertThat(String.valueOf(todos.get(0).get("end_time"))).startsWith("19:30");
        assertThat(String.valueOf(todos.get(1).get("start_time"))).startsWith("18:00");
        assertThat(todos.get(2).get("start_time")).isNull();

        assertThat(jdbc.queryForObject("select status from goal where goal_id = ?", String.class, goalId))
                .isEqualTo("IN_PROGRESS");
        JsonNode plans = call(get("/api/v1/goals/ai/plans").param("goalId", String.valueOf(goalId)), user, null, 200)
                .at("/data/plans");
        assertThat(plans.get(0).get("selected").asBoolean()).isTrue();
        assertThat(plans.get(1).get("selected").asBoolean()).isFalse();
    }

    @Test
    void 다시_고르거나_다른_플랜을_고르면_409() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        JsonNode plans = generatePlans(user, goalId).get("plans");
        long planA = plans.at("/0/planId").asLong();
        long planB = plans.at("/1/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);

        JsonNode again = call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 409);
        assertThat(again.at("/error/code").asString()).isEqualTo("AI_PLAN_ALREADY_SELECTED");
        JsonNode other = call(post("/api/v1/goals/ai/plans/" + planB + "/select"), user, null, 409);
        assertThat(other.at("/error/code").asString()).isEqualTo("GOAL_NOT_PLANNING");
        assertThat(jdbc.queryForObject("select count(*) from todo where goal_id = ?", Integer.class, goalId))
                .isEqualTo(92);
    }

    @Test
    void 목표_기간이_바뀐_플랜은_409_남의_플랜은_403_없는_플랜은_404() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();

        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user(), null, 403);
        call(post("/api/v1/goals/ai/plans/999999999/select"), user, null, 404);

        jdbc.update("update goal set end_date = '2027-01-31' where goal_id = ?", goalId);
        JsonNode error = call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 409);
        assertThat(error.at("/error/code").asString()).isEqualTo("AI_PLAN_OUTDATED");
        assertThat(jdbc.queryForObject("select status from goal where goal_id = ?", String.class, goalId))
                .isEqualTo("PLANNING");
    }

    private void schedule(User user, String type, String title, String start, String end, boolean allDay,
            String rule) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("type", type, "title", title, "startAt", start,
                "endAt", end, "allDay", allDay));
        if (rule != null) {
            body.put("repeatRule", rule);
        }
        call(post("/api/v1/calendar/schedules"), user, body, 201);
    }
}
