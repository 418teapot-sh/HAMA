package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.goalai.service.BusyTimeReader;
import com.hama.domain.todo.repository.TodoRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class TodoFrontendContractIntegrationTest extends GoalAiTestSupport {
    @Autowired private BusyTimeReader busyTimes;
    @Autowired private TodoRepository todos;

    private long task(User user, String category) throws Exception {
        return call(post("/api/v1/todos"), user, Map.of("category", category, "content", "책 읽기",
                "todoDate", "2026-10-01"), 201).at("/data/todoId").asLong();
    }

    private long goal(User user) throws Exception {
        long id = planningGoal(user);
        jdbc.update("update goal set status='IN_PROGRESS' where goal_id=?", id);
        return id;
    }

    private long manualGoalTodo(User user, long goal) throws Exception {
        return call(post("/api/v1/todos"), user, Map.of("category", "AI_GOAL_TASK", "goalId", goal,
                "content", "직접 추가", "todoDate", "2026-10-01"), 201).at("/data/todoId").asLong();
    }

    private JsonNode daily(User user) throws Exception {
        return call(get("/api/v1/todos").param("date", "2026-10-01"), user, null, 200).get("data");
    }

    private JsonNode convert(User user, Object body, int expected) throws Exception {
        return call(post("/api/v1/calendar/category-conversions"), user, body, expected);
    }

    @Test
    void 전환의_잘못된_시간과_타입은_원본을_바꾸지_않고_무시간_기타도_내보낸다() throws Exception {
        User user = user();
        long id = task(user, "TASK");
        call(patch("/api/v1/todos/" + id), user, Map.of("category", "OTHER", "startTime", "19:00"), 400);
        assertThat(daily(user).at("/pending/0/category").asString()).isEqualTo("TASK");
        assertThat(convert(user, Map.of("sourceType", "OTHER", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"), 400)
                .at("/error/code").asString()).isEqualTo("CALENDAR_CONVERSION_INVALID");
        convert(user, Map.of("sourceType", "TASK", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T16:00:00", "endAt", "2026-10-01T15:00:00"), 400);
        assertThat(todos.findById(id).orElseThrow().getDeletedAt()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from schedule where user_id=?", Integer.class, user.id())).isZero();
        long schedule = convert(user, Map.of("sourceType", "TASK", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"), 200).at("/data/refId").asLong();
        convert(user, Map.of("sourceType", "PERSONAL", "refId", schedule, "targetType", "OTHER",
                "todoDate", "2026-10-01", "startTime", "15:00"), 400);
        call(get("/api/v1/calendar/schedules/" + schedule), user, null, 200);
        long other = convert(user, Map.of("sourceType", "PERSONAL", "refId", schedule, "targetType", "OTHER",
                "todoDate", "2026-10-01"), 200).at("/data/refId").asLong();
        String file = mvc.perform(get("/api/v1/calendar/export").param("from", "2026-10-01").param("to", "2026-10-01")
                .param("types", "OTHER").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(file).contains("UID:hama-OTHER-" + other + "@hama.app", "DTSTART;VALUE=DATE:20261001", "DTEND;VALUE=DATE:20261002");
    }

    @Test
    void 동시_일정전환은_원본_하나에서_대상_하나만_만든다() throws Exception {
        User user = user();
        long id = task(user, "TASK");
        String body = json.writeValueAsString(Map.of("sourceType", "TASK", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/calendar/category-conversions").contentType("application/json").content(body)
                        .header("Authorization", "Bearer " + user.token())).andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            assertThat(List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 404);
        }
        assertThat(jdbc.queryForObject("select count(*) from schedule where user_id=? and deleted_at is null", Integer.class, user.id())).isOne();
        assertThat(todos.findById(id).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void 기타를_저장하고_날짜_필터_캘린더_ICS_빈시간에_같이_반영한다() throws Exception {
        User user = user();
        long id = call(post("/api/v1/todos"), user, Map.of("category", "OTHER", "content", "기타 일정",
                "todoDate", "2026-10-01", "startTime", "09:00", "endTime", "10:00"), 201)
                .at("/data/todoId").asLong();
        var item = call(get("/api/v1/todos").param("date", "2026-10-01").param("category", "OTHER"),
                user, null, 200).at("/data/pending/0");
        assertThat(item.get("todoId").asLong()).isEqualTo(id);
        assertThat(item.get("origin").asString()).isEqualTo("MANUAL");
        assertThat(item.get("aiGenerated").asBoolean()).isFalse();
        var calendar = call(get("/api/v1/calendar").param("from", "2026-10-01").param("to", "2026-10-01")
                .param("types", "OTHER"), user, null, 200).at("/data/items");
        assertThat(calendar.size()).isOne();
        assertThat(calendar.get(0).get("type").asString()).isEqualTo("OTHER");
        String file = mvc.perform(get("/api/v1/calendar/export").param("from", "2026-10-01").param("to", "2026-10-01")
                .param("types", "OTHER").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(file).contains("UID:hama-OTHER-" + id + "@hama.app");
        assertThat(busyTimes.read(user.id(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), Set.of())
                .get(LocalDate.of(2026, 10, 1))).anySatisfy(interval -> {
                    assertThat(interval.start()).isEqualTo(540);
                    assertThat(interval.end()).isEqualTo(600);
                });
        var plans = generatePlans(user, planningGoal(user));
        call(post("/api/v1/goals/ai/plans/" + plans.at("/plans/0/planId").asLong() + "/select"), user, null, 200);
        assertThat(jdbc.queryForObject("select min(start_time) from todo where user_id=? and origin='AI' and todo_date='2026-10-01'",
                String.class, user.id())).startsWith("10:00");
        User other = user();
        assertThat(call(get("/api/v1/calendar").param("from", "2026-10-01").param("to", "2026-10-01")
                .param("types", "OTHER"), other, null, 200).at("/data/items").size()).isZero();
    }

    @Test
    void 기타_목표연결과_잘못된_category_patch를_거절하고_같은_ID로_분류를_바꾼다() throws Exception {
        User user = user();
        call(post("/api/v1/todos"), user, Map.of("category", "OTHER", "content", "책", "todoDate", "2026-10-01", "goalId", 1), 400);
        long id = task(user, "TASK");
        call(patch("/api/v1/todos/" + id), user, Map.of("category", "OTHER", "content", "변경"), 200);
        assertThat(daily(user).at("/pending/0/category").asString()).isEqualTo("OTHER");
        for (String value : List.of("null", "123", "[]", "\"AI_GOAL_TASK\"", "\"PERSONAL\"")) {
            call(patch("/api/v1/todos/" + id), user, "{\"category\":" + value + "}", 400);
        }
        convert(user, Map.of("sourceType", "OTHER", "refId", id, "targetType", "TASK"), 200);
        assertThat(daily(user).at("/pending/0/todoId").asLong()).isEqualTo(id);
        assertThat(daily(user).at("/pending/0/category").asString()).isEqualTo("TASK");
        User other = user();
        call(patch("/api/v1/todos/" + id), other, Map.of("category", "OTHER"), 403);
        call(patch("/api/v1/todos/" + id + "/complete"), user, null, 200);
        call(patch("/api/v1/todos/" + id), user, Map.of("category", "OTHER"), 409);
    }

    @Test
    void AI_생성은_수정삭제를_막지만_완료_메모_미루기_취소는_가능하다() throws Exception {
        User user = user();
        long goal = planningGoal(user);
        long plan = generatePlans(user, goal).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + plan + "/select"), user, null, 200);
        long id = daily(user).at("/pending/0/todoId").asLong();
        assertThat(daily(user).at("/pending/0/aiGenerated").asBoolean()).isTrue();
        assertThat(daily(user).at("/pending/0/origin").asString()).isEqualTo("AI");
        User other = user();
        assertThat(call(patch("/api/v1/todos/" + id), other, Map.of("content", "수정"), 403).at("/error/code").asString()).isEqualTo("FORBIDDEN");
        assertThat(call(patch("/api/v1/todos/" + id), user, Map.of("content", "수정"), 409).at("/error/code").asString())
                .isEqualTo("TODO_AI_EDIT_NOT_ALLOWED");
        assertThat(call(patch("/api/v1/todos/" + id), user, Map.of("category", "TASK"), 409).at("/error/code").asString())
                .isEqualTo("TODO_CATEGORY_CHANGE_NOT_ALLOWED");
        assertThat(call(delete("/api/v1/todos/" + id), user, null, 409).at("/error/code").asString()).isEqualTo("TODO_AI_EDIT_NOT_ALLOWED");
        call(patch("/api/v1/todos/" + id + "/complete"), user, null, 200);
        call(patch("/api/v1/todos/state/" + id), user, Map.of("statusNote", "오늘 상태"), 200);
        assertThat(daily(user).at("/completed/0/aiGenerated").asBoolean()).isTrue();
        call(patch("/api/v1/todos/" + id + "/uncomplete"), user, null, 200);
        call(patch("/api/v1/todos/" + id + "/postpone"), user, null, 200);
        assertThat(jdbc.queryForObject("select postponed_count from todo where todo_id=?", Integer.class, id)).isOne();
        assertThat(jdbc.queryForObject("select status_note from todo where todo_id=?", String.class, id)).isEqualTo("오늘 상태");
    }

    @Test
    void 직접추가와_기존출처미확인을_구분하고_요청으로_AI출처를_조작할수없다() throws Exception {
        User user = user();
        long goal = goal(user);
        long id = call(post("/api/v1/todos"), user, """
                {"category":"AI_GOAL_TASK","goalId":%d,"content":"직접","todoDate":"2026-10-01","origin":"AI","aiGenerated":true}
                """.formatted(goal), 201).at("/data/todoId").asLong();
        assertThat(daily(user).at("/pending/0/origin").asString()).isEqualTo("MANUAL");
        assertThat(daily(user).at("/pending/0/aiGenerated").asBoolean()).isFalse();
        call(patch("/api/v1/todos/" + id), user, Map.of("content", "직접 수정"), 200);
        assertThat(call(patch("/api/v1/todos/" + id), user, Map.of("category", "TASK"), 409).at("/error/code").asString())
                .isEqualTo("TODO_CATEGORY_CHANGE_NOT_ALLOWED");
        jdbc.update("update todo set origin='LEGACY_UNKNOWN' where todo_id=?", id);
        assertThat(daily(user).at("/pending/0/origin").asString()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(daily(user).at("/pending/0/aiGenerated").isNull()).isTrue();
        call(patch("/api/v1/todos/" + id), user, Map.of("content", "기존 권한 유지"), 200);
        call(delete("/api/v1/todos/" + id), user, null, 200);
    }

    @Test
    void 완료취소는_메모를_보존하고_미완료_중복취소_권한_삭제를_검증한다() throws Exception {
        User user = user();
        long id = task(user, "OTHER");
        assertThat(call(patch("/api/v1/todos/" + id + "/uncomplete"), user, null, 409).at("/error/code").asString())
                .isEqualTo("TODO_NOT_COMPLETED");
        call(patch("/api/v1/todos/" + id + "/complete"), user, null, 200);
        call(patch("/api/v1/todos/state/" + id), user, Map.of("statusNote", "보존"), 200);
        var response = call(patch("/api/v1/todos/" + id + "/uncomplete"), user, null, 200).get("data");
        assertThat(response.get("status").asString()).isEqualTo("PENDING");
        assertThat(response.get("completedAt").isNull()).isTrue();
        assertThat(response.get("goalProgressRate").isNull()).isTrue();
        assertThat(todos.findById(id).orElseThrow().getStatusNote()).isEqualTo("보존");
        call(patch("/api/v1/todos/" + id), user, Map.of("content", "취소 후 수정"), 200);
        User other = user();
        call(patch("/api/v1/todos/" + id + "/uncomplete"), other, null, 403);
        call(delete("/api/v1/todos/" + id), user, null, 200);
        call(patch("/api/v1/todos/" + id + "/uncomplete"), user, null, 404);
        call(patch("/api/v1/todos/" + id + "/uncomplete"), null, null, 401);
        convert(null, Map.of("sourceType", "TASK", "refId", id, "targetType", "OTHER"), 401);
    }

    @Test
    void 동시_완료취소는_한번만_성공한다() throws Exception {
        User user = user();
        long id = task(user, "TASK");
        call(patch("/api/v1/todos/" + id + "/complete"), user, null, 200);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await();
                return mvc.perform(patch("/api/v1/todos/" + id + "/uncomplete")
                        .header("Authorization", "Bearer " + user.token())).andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            assertThat(List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void 일정전환은_일시필수이고_원본삭제와_새항목생성_메모보존을_같이처리한다() throws Exception {
        User user = user();
        long id = task(user, "OTHER");
        call(patch("/api/v1/todos/state/" + id), user, Map.of("statusNote", "보존 메모"), 200);
        convert(user, Map.of("sourceType", "OTHER", "refId", id, "targetType", "PERSONAL"), 400);
        assertThat(todos.findById(id).orElseThrow().getDeletedAt()).isNull();
        User other = user();
        convert(other, Map.of("sourceType", "OTHER", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"), 403);
        var result = convert(user, Map.of("sourceType", "OTHER", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"), 200).get("data");
        long schedule = result.get("refId").asLong();
        assertThat(result.get("type").asString()).isEqualTo("PERSONAL");
        assertThat(todos.findById(id).orElseThrow().getDeletedAt()).isNotNull();
        var detail = call(get("/api/v1/calendar/schedules/" + schedule), user, null, 200).get("data");
        assertThat(detail.get("memo").asString()).isEqualTo("보존 메모");
        assertThat(detail.get("startAt").asString()).isEqualTo("2026-10-01T15:00:00");
        convert(other, Map.of("sourceType", "PERSONAL", "refId", schedule, "targetType", "TASK", "todoDate", "2026-10-02"), 403);
        var back = convert(user, Map.of("sourceType", "PERSONAL", "refId", schedule, "targetType", "TASK", "todoDate", "2026-10-02"), 200).get("data");
        assertThat(back.get("type").asString()).isEqualTo("TASK");
        assertThat(todos.findById(back.get("refId").asLong()).orElseThrow().getStatusNote()).isEqualTo("보존 메모");
        call(get("/api/v1/calendar/schedules/" + schedule), user, null, 404);
        convert(user, Map.of("sourceType", "PERSONAL", "refId", schedule, "targetType", "OTHER", "todoDate", "2026-10-02"), 404);
    }

    @Test
    void 실패한_전환은_원본을_유지하고_완료_고정_반복_목표투두를_보호한다() throws Exception {
        User user = user();
        long id = task(user, "TASK");
        assertThat(convert(user, Map.of("sourceType", "OTHER", "refId", id, "targetType", "TASK"), 400)
                .at("/error/code").asString()).isEqualTo("CALENDAR_CONVERSION_INVALID");
        assertThat(todos.findById(id).orElseThrow().getCategory().name()).isEqualTo("TASK");
        call(patch("/api/v1/todos/" + id), user, Map.of("content", "가".repeat(101)), 200);
        convert(user, Map.of("sourceType", "TASK", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"), 400);
        assertThat(todos.findById(id).orElseThrow().getDeletedAt()).isNull();
        call(patch("/api/v1/todos/" + id + "/complete"), user, null, 200);
        convert(user, Map.of("sourceType", "TASK", "refId", id, "targetType", "PERSONAL",
                "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"), 409);
        long goalTodo = manualGoalTodo(user, goal(user));
        convert(user, Map.of("sourceType", "TASK", "refId", goalTodo, "targetType", "OTHER"), 409);
        for (String type : List.of("FIXED", "PERSONAL")) {
            Map<String,Object> fields = new java.util.HashMap<>(Map.of("type", type, "title", "일정", "startAt", "2026-10-01T15:00:00", "endAt", "2026-10-01T16:00:00"));
            if (type.equals("PERSONAL")) fields.put("repeatRule", "FREQ=DAILY");
            long schedule = call(post("/api/v1/calendar/schedules"), user, fields, 201).at("/data/scheduleId").asLong();
            assertThat(convert(user, Map.of("sourceType", "PERSONAL", "refId", schedule, "targetType", "OTHER", "todoDate", "2026-10-01"), 409)
                    .at("/error/code").asString()).isEqualTo("CALENDAR_CONVERSION_NOT_ALLOWED");
            call(get("/api/v1/calendar/schedules/" + schedule), user, null, 200);
        }
    }
}
