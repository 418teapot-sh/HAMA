package com.hama.domain.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.goalai.GoalAiTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarPlanIntegrationTest extends GoalAiTestSupport {
    @Test
    void 집중플랜을_선택한_뒤_월간_124개_투두를_조회하고_내보낸다() throws Exception {
        User user = user();
        long goal = planningGoal(user);
        String dailyTasks = """
                [{"content":"듣기","sessionsPerWeek":7,"minutes":30},
                 {"content":"읽기","sessionsPerWeek":7,"minutes":30},
                 {"content":"단어","sessionsPerWeek":7,"minutes":30},
                 {"content":"복습","sessionsPerWeek":7,"minutes":30}]
                """;
        AI.answer("""
                {"plans":[
                  {"variant":"A","title":"여유형","milestones":[{"title":"학습","weeklyTasks":%s}]},
                  {"variant":"B","title":"집중형","milestones":[{"title":"학습","weeklyTasks":%s}]}]}
                """.formatted(dailyTasks, dailyTasks));
        long plan = call(post("/api/v1/goals/ai/plans"), user, Map.of("goalId", goal), 201)
                .at("/data/plans/1/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + plan + "/select"), user, null, 200);
        var items = call(get("/api/v1/calendar").param("from", "2026-10-01")
                .param("to", "2026-10-31").param("types", "AI_GOAL"), user, null, 200).at("/data/items");
        assertThat(items.size()).isEqualTo(124);
        String file = mvc.perform(get("/api/v1/calendar/export").param("from", "2026-10-01")
                .param("to", "2026-10-31").param("types", "AI_GOAL")
                .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(file.split("BEGIN:VEVENT", -1)).hasSize(125);
        assertThat(file).contains("UID:hama-AI_GOAL-");
        long todo = jdbc.queryForObject("select min(todo_id) from todo where goal_id=? and todo_date='2026-10-01'",Long.class,goal);
        var postponed = call(patch("/api/v1/todos/" + todo + "/postpone"),user,null,200).get("data");
        assertThat(postponed.get("todoDate").asString()).isEqualTo("2026-10-02");
        assertThat(postponed.get("postponedCount").asInt()).isOne();
        assertThat(call(get("/api/v1/calendar").param("from","2026-10-02").param("to","2026-10-02")
                .param("types","AI_GOAL"),user,null,200).at("/data/items").size()).isEqualTo(5);
        User other = user();
        assertThat(call(get("/api/v1/calendar").param("from", "2026-10-01")
                .param("to", "2026-10-31"), other, null, 200).at("/data/items").size()).isZero();
    }
}
