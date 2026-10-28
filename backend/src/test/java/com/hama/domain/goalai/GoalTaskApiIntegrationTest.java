package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalTaskApiIntegrationTest extends GoalAiTestSupport {

    @Test
    void 플랜을_선택하지_않은_목표는_빈_할_일_목록이다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        generatePlans(user, goalId);

        JsonNode data = call(get("/api/v1/goals/" + goalId + "/tasks"), user, null, 200).get("data");

        assertThat(data.get("goalId").asLong()).isEqualTo(goalId);
        // 오늘(2026-10-01)은 목요일이라 이번 주는 9/28(월) ~ 10/4(일)입니다.
        assertThat(data.get("weekStart").asString()).isEqualTo("2026-09-28");
        assertThat(data.get("weekEnd").asString()).isEqualTo("2026-10-04");
        assertThat(data.get("tasks").size()).isZero();
    }

    @Test
    void 선택한_플랜의_할_일별로_전체와_이번_주_투두_수를_센다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planB = generatePlans(user, goalId).at("/plans/1/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planB + "/select"), user, null, 200);
        jdbc.update("""
                update todo set status = 'COMPLETED', completed_at = now(6)
                where goal_id = ? and content = '영어 단어 50개 외우기' and todo_date in ('2026-10-01', '2026-10-15')
                """, goalId);

        JsonNode tasks = call(get("/api/v1/goals/" + goalId + "/tasks"), user, null, 200).at("/data/tasks");

        assertThat(tasks.size()).isEqualTo(2);
        JsonNode daily = tasks.get(0);
        assertThat(daily.get("content").asString()).isEqualTo("영어 단어 50개 외우기");
        assertThat(daily.get("timesPerWeek").asInt()).isEqualTo(7);
        assertThat(daily.get("minutes").asInt()).isEqualTo(90);
        assertThat(daily.get("todoTotal").asLong()).isEqualTo(92);
        assertThat(daily.get("todoCompleted").asLong()).isEqualTo(2);
        // 목표는 10/1 에 시작하므로 이번 주에는 10/1 ~ 10/4 의 4개만 있습니다.
        assertThat(daily.get("weekTodoTotal").asLong()).isEqualTo(4);
        assertThat(daily.get("weekTodoCompleted").asLong()).isEqualTo(1);

        JsonNode weekly = tasks.get(1);
        assertThat(weekly.get("content").asString()).isEqualTo("모의고사 1회");
        assertThat(weekly.get("todoTotal").asLong()).isEqualTo(14);
        assertThat(weekly.get("weekTodoTotal").asLong()).isEqualTo(1);
        assertThat(weekly.get("weekTodoCompleted").asLong()).isZero();

        jdbc.update("update todo set deleted_at = now(6) where goal_id = ? and content = '모의고사 1회'", goalId);
        JsonNode afterDelete = call(get("/api/v1/goals/" + goalId + "/tasks"), user, null, 200).at("/data/tasks");
        assertThat(afterDelete.get(1).get("todoTotal").asLong()).isZero();
    }

    @Test
    void 남의_목표는_403_없는_목표는_404() throws Exception {
        User user = user();
        long goalId = planningGoal(user);

        call(get("/api/v1/goals/" + goalId + "/tasks"), user(), null, 403);
        call(get("/api/v1/goals/999999999/tasks"), user, null, 404);
    }
}
