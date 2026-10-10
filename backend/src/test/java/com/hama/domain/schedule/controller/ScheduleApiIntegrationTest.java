package com.hama.domain.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import com.hama.domain.schedule.repository.ScheduleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
class ScheduleApiIntegrationTest {

    private static final String BASE = "/api/v1/calendar/schedules";
    private static final String FIXED = """
            {"type":"FIXED","title":"회사","startAt":"2026-10-05T09:00:00","endAt":"2026-10-05T18:00:00",
             "allDay":false,"repeatRule":"FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR","memo":"회사 출근"}
            """;

    @TestBean(name = "be3Clock", methodName = "fixedClock")
    private Clock clock;

    static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-10-03T15:05:06Z"), ZoneId.of("Asia/Seoul"));
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper mapper;
    @Autowired
    private ScheduleRepository schedules;

    @Test
    void OpenAPI의_일정과_투두_생성응답은_서로다른_ID를_문서화한다() throws Exception {
        JsonNode document = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertThat(createdProperties(document, BASE).has("scheduleId")).isTrue();
        assertThat(createdProperties(document, BASE).has("todoId")).isFalse();
        assertThat(createdProperties(document, "/api/v1/todos").has("todoId")).isTrue();
        assertThat(createdProperties(document, "/api/v1/todos").has("scheduleId")).isFalse();
    }

    private JsonNode createdProperties(JsonNode document, String path) {
        JsonNode content = document.get("paths").get(path).get("post").get("responses").get("201").get("content");
        String envelopeRef = content.properties().iterator().next().getValue().get("schema").get("$ref").asString();
        String dataRef = document.at(envelopeRef.substring(1)).get("properties").get("data").get("$ref").asString();
        return document.at(dataRef.substring(1)).get("properties");
    }

    @Test
    void 실제_JWT로_고정일정을_생성하고_모든_명세필드를_조회한다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        MvcResult result = mvc.perform(auth(get(BASE + "/" + id), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduleId").value(id))
                .andExpect(jsonPath("$.data.type").value("FIXED"))
                .andExpect(jsonPath("$.data.title").value("회사"))
                .andExpect(jsonPath("$.data.startAt").value("2026-10-05T09:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-10-05T18:00:00"))
                .andExpect(jsonPath("$.data.allDay").value(false))
                .andExpect(jsonPath("$.data.repeatRule").value("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
                .andExpect(jsonPath("$.data.memo").value("회사 출근"))
                .andExpect(header().exists("X-Trace-Id")).andReturn();
        assertThat(body(result).at("/traceId").asString())
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        Schedule stored = schedules.findById(id).orElseThrow();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.getDeletedAt()).isNull();
    }

    @Test
    void 개인일정의_선택필드는_생략할수있고_allDay는_false다() throws Exception {
        String owner = signup();
        long id = create(owner, """
                {"type":"PERSONAL","title":"저녁 약속","startAt":"2026-10-05T19:00:00","endAt":"2026-10-05T21:00:00"}
                """);
        Schedule stored = schedules.findById(id).orElseThrow();
        assertThat(stored.getType()).isEqualTo(ScheduleType.PERSONAL);
        assertThat(stored.isAllDay()).isFalse();
        assertThat(stored.getRepeatRule()).isNull();
        assertThat(stored.getMemo()).isNull();
    }

    @Test
    void 미인증_요청은_전체_일정_API에서_401이다() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                post(BASE).content(FIXED), get(BASE + "/1"),
                patch(BASE + "/1").content("{}"), delete(BASE + "/1"))) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void 타인_일정은_403이고_없는_일정은_404다() throws Exception {
        String owner = signup();
        String other = signup();
        long id = create(owner, FIXED);
        for (MockHttpServletRequestBuilder request : List.of(get(BASE + "/" + id),
                patch(BASE + "/" + id).content("{}"), delete(BASE + "/" + id))) {
            mvc.perform(auth(request.contentType(MediaType.APPLICATION_JSON), other))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        for (MockHttpServletRequestBuilder request : List.of(get(BASE + "/9223372036854775807"),
                patch(BASE + "/9223372036854775807").content("{}"), delete(BASE + "/9223372036854775807"))) {
            mvc.perform(auth(request.contentType(MediaType.APPLICATION_JSON), owner))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SCHEDULE_NOT_FOUND"));
        }
    }

    @Test
    void 부분수정은_생략필드를_유지하고_선택null만_지운다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        update(owner, id, "{\"type\":\"PERSONAL\",\"title\":\"수정 제목\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("PERSONAL"))
                .andExpect(jsonPath("$.data.title").value("수정 제목"))
                .andExpect(jsonPath("$.data.startAt").value("2026-10-05T09:00:00"))
                .andExpect(jsonPath("$.data.repeatRule").value("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
                .andExpect(jsonPath("$.data.memo").value("회사 출근"));
        update(owner, id, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("회사 출근"));
        MvcResult cleared = update(owner, id, "{\"repeatRule\":null,\"memo\":null}")
                .andExpect(status().isOk()).andReturn();
        assertThat(body(cleared).at("/data/repeatRule").isNull()).isTrue();
        assertThat(body(cleared).at("/data/memo").isNull()).isTrue();
        for (String field : List.of("type", "title", "startAt", "endAt", "allDay")) {
            update(owner, id, "{\"" + field + "\":null}").andExpect(status().isBadRequest());
        }
        assertThat(schedules.findById(id).orElseThrow().getTitle()).isEqualTo("수정 제목");
    }

    @Test
    void 필수값과_제목길이를_공통_검증응답으로_반환한다() throws Exception {
        String owner = signup();
        mvc.perform(auth(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.type").exists())
                .andExpect(jsonPath("$.error.fields.title").exists())
                .andExpect(jsonPath("$.error.fields.startAt").exists())
                .andExpect(jsonPath("$.error.fields.endAt").exists());
        long id = create(owner, FIXED);
        update(owner, id, "{\"title\":\"" + "가".repeat(101) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.title").exists());
        update(owner, id, "{\"title\":\"  \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.title").exists());
        update(owner, id, "{\"type\":\"AI_GOAL\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.type").exists());
        mvc.perform(auth(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(FIXED.replace("\"allDay\":false", "\"allDay\":null")), owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 잘못된_JSON타입과_날짜형식은_저장값을_바꾸지_않는다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        for (String fragment : List.of(
                "\"title\":123", "\"memo\":true", "\"repeatRule\":[]", "\"type\":0",
                "\"allDay\":\"false\"", "\"allDay\":1", "\"startAt\":[2026,10,5,9,0]",
                "\"startAt\":\"2026-02-30T09:00:00\"", "\"startAt\":\"2026-10-05T24:00:00\"",
                "\"startAt\":\"2026-10-05T09:00\"", "\"startAt\":\"2026-10-05T09:00:00.123\"",
                "\"startAt\":\"2026-10-05T09:00:00+09:00\"", "\"startAt\":\"\"")) {
            update(owner, id, "{" + fragment + "}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("PARSING_ERROR"));
        }
        Schedule stored = schedules.findById(id).orElseThrow();
        assertThat(stored.getStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 9, 0));
        assertThat(stored.getTitle()).isEqualTo("회사");
    }

    @Test
    void 시간순서와_DB날짜범위를_검증하고_실패한_복합수정은_원복된다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        for (String start : List.of("2026-10-05T18:00:00", "2026-10-05T19:00:00", "0999-10-05T09:00:00")) {
            update(owner, id, "{\"title\":\"실패 제목\",\"startAt\":\"" + start + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SCHEDULE_INVALID_TIME"));
        }
        assertThat(schedules.findById(id).orElseThrow().getTitle()).isEqualTo("회사");
        mvc.perform(auth(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(FIXED.replace("2026-10-05T18:00:00", "2026-10-05T08:00:00")), owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 종일일정은_종료날짜를_포함하지_않는_자정구간으로_저장한다() throws Exception {
        String owner = signup();
        long id = create(owner, """
                {"type":"PERSONAL","title":"휴가","startAt":"2026-10-05T00:00:00",
                 "endAt":"2026-10-06T00:00:00","allDay":true,"repeatRule":"FREQ=DAILY;UNTIL=20261031"}
                """);
        mvc.perform(auth(get(BASE + "/" + id), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allDay").value(true))
                .andExpect(jsonPath("$.data.endAt").value("2026-10-06T00:00:00"));
        update(owner, id, "{\"endAt\":\"2026-10-05T23:59:59\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_INVALID_ALL_DAY"));
        update(owner, id, "{\"startAt\":\"2026-10-05T01:00:00\"}")
                .andExpect(status().isBadRequest());
        // 종일에서 시간 일정으로 바뀌면 이전 DATE UNTIL을 그대로 유지할 수 없습니다.
        update(owner, id, "{\"allDay\":false}").andExpect(status().isBadRequest());
        update(owner, id, "{\"allDay\":false,\"repeatRule\":null}").andExpect(status().isOk());
    }

    @Test
    void 반복원문을_보존하고_시리즈수정_결과를_다시_검증한다() throws Exception {
        String owner = signup();
        String rule = "count=6;freq=weekly;interval=2;byday=mo,fr";
        long id = create(owner, FIXED.replace("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", rule));
        assertThat(schedules.findById(id).orElseThrow().getRepeatRule()).isEqualTo(rule);
        update(owner, id, "{\"startAt\":\"2026-10-06T09:00:00\",\"endAt\":\"2026-10-06T18:00:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_REPEAT_START_MISMATCH"));
        update(owner, id, "{\"repeatRule\":\"FREQ=DAILY;UNTIL=20261101T000000Z\"}")
                .andExpect(status().isOk());
        for (String invalid : List.of("FREQ=MONTHLY", "FREQ=DAILY;BYMONTH=10", "FREQ=DAILY;COUNT=0",
                "FREQ=DAILY;COUNT=2;UNTIL=20261101T000000Z", "")) {
            update(owner, id, "{\"repeatRule\":\"" + invalid + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SCHEDULE_INVALID_REPEAT_RULE"));
        }
        assertThat(schedules.findById(id).orElseThrow().getRepeatRule())
                .isEqualTo("FREQ=DAILY;UNTIL=20261101T000000Z");
    }

    @Test
    void 메모의_UTF8_TEXT_범위를_검증한다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        update(owner, id, "{\"memo\":\"" + "가".repeat(21845) + "\"}")
                .andExpect(status().isOk());
        update(owner, id, "{\"memo\":\"" + "가".repeat(21846) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_MEMO_TOO_LONG"));
        assertThat(schedules.findById(id).orElseThrow().getMemo()).hasSize(21845);
    }

    @Test
    void 삭제는_KST시각의_소프트삭제이며_JSON_200이고_다시_접근할수없다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        MvcResult deleted = mvc.perform(auth(delete(BASE + "/" + id), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
        assertThat(body(deleted).at("/data").isNull()).isTrue();
        Schedule stored = schedules.findById(id).orElseThrow();
        assertThat(stored.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 0, 5, 6));
        assertThat(stored.getRepeatRule()).isEqualTo("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");
        for (MockHttpServletRequestBuilder request : List.of(get(BASE + "/" + id),
                patch(BASE + "/" + id).content("{}"), delete(BASE + "/" + id))) {
            mvc.perform(auth(request.contentType(MediaType.APPLICATION_JSON), owner))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SCHEDULE_NOT_FOUND"));
        }
    }

    @Test
    void 동시_부분수정은_서로다른_필드를_유실하지_않고_시간불변식을_보존한다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        assertThat(race(
                () -> update(owner, id, "{\"title\":\"새 제목\"}").andReturn().getResponse().getStatus(),
                () -> update(owner, id, "{\"memo\":\"새 메모\"}").andReturn().getResponse().getStatus()))
                .containsOnly(200);
        Schedule updated = schedules.findById(id).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("새 제목");
        assertThat(updated.getMemo()).isEqualTo("새 메모");
        assertThat(race(
                () -> update(owner, id, "{\"startAt\":\"2026-10-05T17:00:00\"}").andReturn().getResponse().getStatus(),
                () -> update(owner, id, "{\"endAt\":\"2026-10-05T10:00:00\"}").andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 400);
        Schedule valid = schedules.findById(id).orElseThrow();
        assertThat(valid.getStartAt()).isBefore(valid.getEndAt());
    }

    @Test
    void 동시_삭제와_수정이_일정을_되살리지_않는다() throws Exception {
        String owner = signup();
        long id = create(owner, FIXED);
        List<Integer> results = race(
                () -> mvc.perform(auth(delete(BASE + "/" + id), owner)).andReturn().getResponse().getStatus(),
                () -> update(owner, id, "{\"title\":\"새 제목\"}").andReturn().getResponse().getStatus());
        assertThat(results.getFirst()).isEqualTo(200);
        assertThat(results.getLast()).isIn(200, 404);
        assertThat(schedules.findById(id).orElseThrow().getDeletedAt()).isNotNull();
        mvc.perform(auth(get(BASE + "/" + id), owner)).andExpect(status().isNotFound());
    }

    private String signup() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"schedule-%s@hama.com","password":"password1234!","name":"일정","termsAgreed":true,"privacyAgreed":true,"ageConfirmed":true}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn();
        return body(result).at("/data/accessToken").asString();
    }

    private long create(String token, String json) throws Exception {
        MvcResult result = mvc.perform(auth(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json), token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andReturn();
        return body(result).at("/data/scheduleId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions update(String token, long id, String json) throws Exception {
        return mvc.perform(auth(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json), token));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private JsonNode body(MvcResult result) {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> gatedFirst = gated(first, ready, start);
            Callable<Integer> gatedSecond = gated(second, ready, start);
            var firstResult = pool.submit(gatedFirst);
            var secondResult = pool.submit(gatedSecond);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        }
    }

    private Callable<Integer> gated(Callable<Integer> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
            }
            return action.call();
        };
    }
}
