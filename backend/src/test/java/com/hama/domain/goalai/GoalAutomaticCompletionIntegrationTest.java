package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GoalAutomaticCompletionIntegrationTest extends GoalAiTestSupport {
    @Test
    void 마지막_투두_완료와_취소가_목표_상태를_함께_바꾼다() throws Exception {
        User user = user();
        long goal = call(post("/api/v1/goals"), user, Map.of("title", "목표", "startDate", "2026-10-01",
                "endDate", "2026-10-31"), 201).at("/data/goalId").asLong();
        jdbc.update("update goal set status = 'IN_PROGRESS' where goal_id = ?", goal);
        long first = task(user, goal);
        long second = task(user, goal);

        call(patch("/api/v1/todos/" + first + "/complete"), user, null, 200);
        assertThat(status(goal)).isEqualTo("IN_PROGRESS");
        call(patch("/api/v1/todos/" + second + "/complete"), user, null, 200);
        assertThat(status(goal)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select auto_completed from goal where goal_id = ?", Boolean.class, goal)).isTrue();
        call(patch("/api/v1/todos/" + first + "/uncomplete"), user, null, 200);
        assertThat(status(goal)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select completed_at from goal where goal_id = ?", java.sql.Timestamp.class, goal)).isNull();
    }

    @Test
    void 미완료_투두를_삭제해_전부_완료가_되면_자동완료하되_투두가_없으면_완료하지_않는다() throws Exception {
        User user = user();
        long goal = call(post("/api/v1/goals"), user, Map.of("title", "목표", "startDate", "2026-10-01",
                "endDate", "2026-10-31"), 201).at("/data/goalId").asLong();
        jdbc.update("update goal set status = 'IN_PROGRESS' where goal_id = ?", goal);
        long done = task(user, goal);
        long pending = task(user, goal);
        call(patch("/api/v1/todos/" + done + "/complete"), user, null, 200);
        call(delete("/api/v1/todos/" + pending), user, null, 200);
        assertThat(status(goal)).isEqualTo("COMPLETED");

        long emptyGoal = call(post("/api/v1/goals"), user, Map.of("title", "빈 목표", "startDate", "2026-10-01",
                "endDate", "2026-10-31"), 201).at("/data/goalId").asLong();
        jdbc.update("update goal set status = 'IN_PROGRESS' where goal_id = ?", emptyGoal);
        long only = task(user, emptyGoal);
        call(delete("/api/v1/todos/" + only), user, null, 200);
        assertThat(status(emptyGoal)).isEqualTo("IN_PROGRESS");
    }

    private long task(User user, long goal) throws Exception {
        return call(post("/api/v1/todos"), user, Map.of("category", "AI_GOAL_TASK", "goalId", goal,
                "content", "할 일", "todoDate", "2026-10-01"), 201).at("/data/todoId").asLong();
    }

    private String status(long goal) {
        return jdbc.queryForObject("select status from goal where goal_id = ?", String.class, goal);
    }
}
