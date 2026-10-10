package com.hama.domain.todo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoStatus;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.domain.todo.service.TodoService;
import com.hama.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoApiIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @TestBean(name = "be3Clock", methodName = "fixedClock")
    private Clock clock;

    static Clock fixedClock() {
        // UTC 10월 3일 / KST 10월 4일로 서버 기본 시간대 의존을 검출합니다.
        return Clock.fixed(Instant.parse("2026-10-03T15:05:06Z"), ZoneId.of("Asia/Seoul"));
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper mapper;
    @Autowired
    private TodoRepository todos;
    @Autowired
    private TodoService service;
    @Autowired
    private UserRepository users;

    private record Session(Long userId, String token) {
    }

    private Session signup() throws Exception {
        String email = "todo-" + UUID.randomUUID() + "@hama.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234!","name":"투두","termsAgreed":true,"privacyAgreed":true,"ageConfirmed":true}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn();
        return new Session(users.findByEmail(email).orElseThrow().getId(),
                body(result).at("/data/accessToken").asString());
    }

    private long task(Session user) throws Exception {
        MvcResult result = mvc.perform(auth(post("/api/v1/todos")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"category":"TASK","content":"책 읽기","todoDate":"2026-10-04",
                                 "startTime":"19:00","endTime":"20:00","goalId":null,"periodGoalId":null}
                                """), user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)).andReturn();
        return body(result).at("/data/todoId").asLong();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Session user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
    }

    private JsonNode body(MvcResult result) {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    void 생성_필수값과_목표참조를_검증하고_시간없는_TASK도_저장한다() throws Exception {
        Session owner = signup();
        mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.category").exists())
                .andExpect(jsonPath("$.error.fields.content").exists())
                .andExpect(jsonPath("$.error.fields.todoDate").exists());
        for (String field : List.of("goalId", "periodGoalId")) {
            mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category":"TASK","content":"책","todoDate":"2026-10-04","%s":1}
                                    """.formatted(field)), owner))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.error.fields." + field).exists());
        }
        mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"category":"AI_GOAL_TASK","content":"책","todoDate":"2026-10-04"}
                        """), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.goalId").exists());
        MvcResult result = mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"TASK\",\"content\":\"책\",\"todoDate\":\"2026-10-04\"}"), owner))
                .andExpect(status().isCreated()).andReturn();
        Todo created = todos.findById(body(result).at("/data/todoId").asLong()).orElseThrow();
        assertThat(created.getStartTime()).isNull();
        assertThat(created.getEndTime()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(TodoStatus.PENDING);
    }

    @Test
    void 생성도_배열과_잘못된_시간_날짜_문자열을_거절한다() throws Exception {
        Session owner = signup();
        for (String invalidFields : List.of(
                "\"startTime\":\"24:00\",\"endTime\":\"01:00\"",
                "\"startTime\":[],\"endTime\":[]",
                "\"startTime\":[19,0],\"endTime\":[20,0]",
                "\"startTime\":\"\",\"endTime\":\"\"",
                "\"startTime\":\"19:00:00\",\"endTime\":\"20:00\"")) {
            mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON).content("""
                            {"category":"TASK","content":"책","todoDate":"2026-10-04",%s}
                            """.formatted(invalidFields)), owner))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("PARSING_ERROR"));
        }
        for (String date : List.of("\"2026-02-30\"", "[2026,10,4]", "\"\"", "\"0000-01-01\"")) {
            mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON).content("""
                            {"category":"TASK","content":"책","todoDate":%s}
                            """.formatted(date)), owner)).andExpect(status().isBadRequest());
        }
        mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"category":"TASK","content":123,"todoDate":"2026-10-04"}
                        """), owner)).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"category":"TASK","content":"책","todoDate":"2026-10-04","startTime":"19:00"}
                        """), owner)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/todos"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending.length()").value(0));
    }

    @Test
    void 로그인하지_않으면_모든_투두_API는_401() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/todos"), post("/api/v1/todos").content("{}"),
                patch("/api/v1/todos/1").content("{}"), patch("/api/v1/todos/1/postpone"),
                patch("/api/v1/todos/1/complete"), patch("/api/v1/todos/state/1").content("{}"),
                delete("/api/v1/todos/1"))) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void 날짜_생략은_KST_오늘이고_본인_활성_투두만_상태별로_조회() throws Exception {
        Session owner = signup();
        long pending = task(owner);
        long completed = task(owner);
        long removed = task(owner);
        task(signup());
        service.createTask(owner.userId(), "다른 날", TODAY.minusDays(1), null, null);
        service.complete(owner.userId(), completed);
        service.delete(owner.userId(), removed);

        MvcResult result = mvc.perform(auth(get("/api/v1/todos"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-10-04"))
                .andExpect(jsonPath("$.data.pending.length()").value(1))
                .andExpect(jsonPath("$.data.pending[0].todoId").value(pending))
                .andExpect(jsonPath("$.data.pending[0].startTime").value("19:00"))
                .andExpect(jsonPath("$.data.pending[0].endTime").value("20:00"))
                .andExpect(jsonPath("$.data.pending[0].postponedCount").value(0))
                .andExpect(jsonPath("$.data.completed.length()").value(1))
                .andExpect(jsonPath("$.data.completed[0].todoId").value(completed))
                .andExpect(jsonPath("$.data.completed[0].completedAt").value("2026-10-04T00:05:06"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();
        assertThat(body(result).at("/data/pending/0/goalId").isNull()).isTrue();
        assertThat(body(result).at("/data/pending/0/goalTitle").isNull()).isTrue();
        assertThat(body(result).at("/data/completed/0/statusNote").isNull()).isTrue();
        assertThat(body(result).at("/traceId").asString())
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        mvc.perform(auth(get("/api/v1/todos").param("category", "AI_GOAL_TASK"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending.length()").value(0));
        mvc.perform(auth(get("/api/v1/todos").param("date", "2026-10-03")
                        .param("category", "TASK"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending[0].content").value("다른 날"));
    }

    @Test
    void 없는_ID와_타인_ID를_구분한다() throws Exception {
        Session owner = signup();
        Session other = signup();
        long id = task(owner);
        for (String path : List.of("/" + id, "/" + id + "/postpone",
                "/" + id + "/complete", "/state/" + id)) {
            mvc.perform(auth(patch("/api/v1/todos" + path).contentType(MediaType.APPLICATION_JSON)
                            .content("{}"), other))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        mvc.perform(auth(delete("/api/v1/todos/" + id), other)).andExpect(status().isForbidden());
        mvc.perform(auth(patch("/api/v1/todos/9223372036854775807/complete"), owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
    }

    @Test
    void 부분수정은_생략과_null을_구분하고_병합한_시간을_검증한다() throws Exception {
        Session owner = signup();
        long id = task(owner);
        mvc.perform(auth(patch("/api/v1/todos/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정한 할일\"}"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startTime").value("19:00"));
        mvc.perform(auth(patch("/api/v1/todos/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"21:00\"}"), owner))
                .andExpect(status().isBadRequest());
        assertThat(todos.findById(id).orElseThrow().getStartTime()).isEqualTo(LocalTime.of(19, 0));
        mvc.perform(auth(patch("/api/v1/todos/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":null}"), owner))
                .andExpect(status().isBadRequest());
        MvcResult result = mvc.perform(auth(patch("/api/v1/todos/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":null,\"endTime\":null}"), owner))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(result).at("/data/startTime").isNull()).isTrue();
        assertThat(body(result).at("/data/endTime").isNull()).isTrue();
        mvc.perform(auth(patch("/api/v1/todos/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":null}"), owner)).andExpect(status().isBadRequest());
        mvc.perform(auth(patch("/api/v1/todos/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.content").exists());
    }

    @Test
    void 미루기는_본문생략과_빈객체_모두_다음날로_이동하고_시간을_유지한다() throws Exception {
        Session owner = signup();
        long id = task(owner);
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoDate").value("2026-10-05"))
                .andExpect(jsonPath("$.data.startTime").value("19:00"))
                .andExpect(jsonPath("$.data.postponedCount").value(1));
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoDate").value("2026-10-06"))
                .andExpect(jsonPath("$.data.postponedCount").value(2));
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"2026-10-06\"}"), owner))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"2026-10-01\"}"), owner))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":null}"), owner))
                .andExpect(status().isBadRequest());
        assertThat(todos.findById(id).orElseThrow().getPostponedCount()).isEqualTo(2);
    }

    @Test
    void 완료_후에는_수정과_미루기와_재완료를_막고_메모와_삭제는_허용한다() throws Exception {
        Session owner = signup();
        long id = task(owner);
        MvcResult result = mvc.perform(auth(patch("/api/v1/todos/" + id + "/complete"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-10-04T00:05:06"))
                .andReturn();
        assertThat(body(result).at("/data/goalProgressRate").isNull()).isTrue();
        for (String suffix : List.of("", "/postpone", "/complete")) {
            mvc.perform(auth(patch("/api/v1/todos/" + id + suffix)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("TODO_ALREADY_COMPLETED"));
        }
        mvc.perform(auth(patch("/api/v1/todos/state/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statusNote\":\"잘했어요\"}"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statusNote").value("잘했어요"));
        mvc.perform(auth(patch("/api/v1/todos/state/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statusNote").value("잘했어요"));
        mvc.perform(auth(patch("/api/v1/todos/state/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statusNote\":\"\"}"), owner))
                .andExpect(status().isOk());
        assertThat(todos.findById(id).orElseThrow().getStatusNote()).isNull();
        MvcResult deleted = mvc.perform(auth(delete("/api/v1/todos/" + id), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
        assertThat(body(deleted).at("/data").isNull()).isTrue();
        assertThat(todos.findById(id).orElseThrow().getDeletedAt()).isNotNull();
        for (String suffix : List.of("", "/postpone", "/complete")) {
            mvc.perform(auth(patch("/api/v1/todos/" + id + suffix)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(auth(patch("/api/v1/todos/state/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/todos/" + id), owner)).andExpect(status().isNotFound());
    }

    @Test
    void 잘못된_날짜와_카테고리와_시간형식은_400() throws Exception {
        Session owner = signup();
        long id = task(owner);
        mvc.perform(auth(get("/api/v1/todos").param("date", "2026-02-30"), owner))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/todos").param("category", "FIXED"), owner))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(patch("/api/v1/todos/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"19:00:30\",\"endTime\":\"20:00\"}"), owner))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetDate\":\"2026-02-30\"}"), owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 동시_완료는_한번만_성공하고_동시_미루기는_갱신을_잃지_않는다() throws Exception {
        Session owner = signup();
        long completeId = task(owner);
        List<Integer> completed = race(() -> mvc.perform(auth(
                patch("/api/v1/todos/" + completeId + "/complete"), owner))
                .andReturn().getResponse().getStatus());
        assertThat(completed).containsExactlyInAnyOrder(200, 409);
        assertThat(todos.findById(completeId).orElseThrow().getStatus()).isEqualTo(TodoStatus.COMPLETED);
        long postponeId = task(owner);
        assertThat(race(() -> mvc.perform(auth(
                patch("/api/v1/todos/" + postponeId + "/postpone"), owner))
                .andReturn().getResponse().getStatus())).containsOnly(200);
        Todo postponed = todos.findById(postponeId).orElseThrow();
        assertThat(postponed.getTodoDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(postponed.getPostponedCount()).isEqualTo(2);
    }

    @Test
    void 문자열_HH_mm_이외의_시간은_기존_값을_바꾸지_않는다() throws Exception {
        Session owner = signup();
        long id = task(owner);
        for (String invalid : List.of(
                "{\"startTime\":\"24:00\",\"endTime\":\"01:00\"}",
                "{\"startTime\":\"\",\"endTime\":\"\"}",
                "{\"startTime\":[19,0],\"endTime\":[20,0]}",
                "{\"startTime\":[],\"endTime\":[]}")) {
            for (String suffix : List.of("", "/postpone")) {
                mvc.perform(auth(patch("/api/v1/todos/" + id + suffix)
                                .contentType(MediaType.APPLICATION_JSON).content(invalid), owner))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error.code").value("PARSING_ERROR"));
            }
        }
        Todo unchanged = todos.findById(id).orElseThrow();
        assertThat(unchanged.getTodoDate()).isEqualTo(TODAY);
        assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(unchanged.getPostponedCount()).isZero();
    }

    @Test
    void 최대_날짜의_기본_미루기는_500_대신_400이고_저장값을_보존한다() throws Exception {
        Session owner = signup();
        long id = task(owner);
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"9999-12-31\"}"), owner))
                .andExpect(status().isOk());
        mvc.perform(auth(patch("/api/v1/todos/" + id + "/postpone"), owner))
                .andExpect(status().isBadRequest());
        Todo unchanged = todos.findById(id).orElseThrow();
        assertThat(unchanged.getTodoDate()).isEqualTo(LocalDate.of(9999, 12, 31));
        assertThat(unchanged.getPostponedCount()).isEqualTo(1);
    }

    private List<Integer> race(Callable<Integer> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> gated = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
                }
                return action.call();
            };
            var first = pool.submit(gated);
            var second = pool.submit(gated);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }
}
