package com.hama.domain.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.hama.domain.calendar.service.IcsWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @MockitoSpyBean private IcsWriter icsWriter;
    @TestBean(name = "be3Clock", methodName = "fixedClock") private Clock clock;

    static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void 일정과_TASK를_날짜별로_통합하고_종일과_완료상태를_보존한다() throws Exception {
        String token = signup();
        long overnight = schedule(token, "FIXED", "야간", "2026-10-03T23:00:00", "2026-10-04T02:00:00", false, null);
        schedule(token, "PERSONAL", "휴가", "2026-10-03T00:00:00", "2026-10-05T00:00:00", true, null);
        todo(token, "시간 없음", "2026-10-04", null, null);
        long timed = todo(token, "완료한 일", "2026-10-04", "11:00", "12:00");
        mvc.perform(auth(patch("/api/v1/todos/" + timed + "/complete"), token)).andExpect(status().isOk());
        JsonNode items = query(token, "2026-10-03", "2026-10-05", null);
        assertThat(items.size()).isEqualTo(6);
        List<JsonNode> night = items.valueStream().filter(item -> item.get("refId").asLong() == overnight
                && item.get("type").asString().equals("FIXED")).toList();
        assertThat(night).hasSize(2);
        assertThat(night.getFirst().get("date").asString()).isEqualTo("2026-10-03");
        assertThat(night.getFirst().get("startTime").asString()).isEqualTo("23:00");
        assertThat(night.getFirst().get("endTime").asString()).isEqualTo("00:00");
        assertThat(night.getLast().get("endTime").asString()).isEqualTo("02:00");
        JsonNode untimed = items.valueStream().filter(item -> item.get("title").asString().equals("시간 없음")).findFirst().orElseThrow();
        assertThat(untimed.get("allDay").asBoolean()).isTrue();
        assertThat(untimed.get("startTime").isNull()).isTrue();
        assertThat(items.valueStream().filter(item -> item.get("title").asString().equals("완료한 일"))
                .findFirst().orElseThrow().get("status").asString()).isEqualTo("COMPLETED");
        assertThat(items.valueStream().map(item -> item.get("date").asString())).doesNotContain("2026-10-05");
    }

    @Test
    void 타입필터와_소유권_삭제_날짜경계를_조회와_내보내기에_함께_적용한다() throws Exception {
        String token = signup();
        String other = signup();
        long fixed = schedule(token, "FIXED", "포함", "2026-10-05T09:00:00", "2026-10-05T10:00:00", false, null);
        schedule(other, "FIXED", "타인", "2026-10-05T09:00:00", "2026-10-05T10:00:00", false, null);
        schedule(token, "PERSONAL", "다른 타입", "2026-10-05T09:00:00", "2026-10-05T10:00:00", false, null);
        schedule(token, "FIXED", "이전 경계", "2026-10-04T23:00:00", "2026-10-05T00:00:00", false, null);
        schedule(token, "FIXED", "다음 경계", "2026-10-06T00:00:00", "2026-10-06T01:00:00", false, null);
        long deleted = schedule(token, "FIXED", "삭제", "2026-10-05T12:00:00", "2026-10-05T13:00:00", false, null);
        mvc.perform(auth(delete("/api/v1/calendar/schedules/" + deleted), token)).andExpect(status().isOk());
        long task = todo(token, "삭제 TASK", "2026-10-05", null, null);
        mvc.perform(auth(delete("/api/v1/todos/" + task), token)).andExpect(status().isOk());
        JsonNode items = query(token, "2026-10-05", "2026-10-05", "FIXED");
        assertThat(items.size()).isOne();
        assertThat(items.get(0).get("refId").asLong()).isEqualTo(fixed);
        String file = export(token, "2026-10-05", "2026-10-05", "FIXED");
        assertThat(file).contains("UID:hama-FIXED-" + fixed + "@hama.app", "SUMMARY:포함");
        assertThat(file).doesNotContain("타인", "다른 타입", "이전 경계", "다음 경계", "삭제");
        assertThat(query(token, "2026-10-05", "2026-10-05", "TASK").isEmpty()).isTrue();
        assertThat(query(token, "2026-10-05", "2026-10-05", "AI_GOAL").isEmpty()).isTrue();
    }

    @Test
    void 반복은_기간_안에_전개하지만_내보내기는_원본_시리즈를_한번만_보존한다() throws Exception {
        String token = signup();
        String rule = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=5";
        long id = schedule(token, "FIXED", "반복", "2026-10-05T09:00:00", "2026-10-05T10:00:00", false, rule);
        schedule(token, "FIXED", "종료된 반복", "2026-10-05T09:00:00", "2026-10-05T10:00:00", false, "FREQ=DAILY;COUNT=2");
        JsonNode items = query(token, "2026-10-19", "2026-11-03", null);
        assertThat(items.valueStream().map(item -> item.get("date").asString()).toList())
                .containsExactly("2026-10-19", "2026-10-21", "2026-11-02");
        String file = export(token, "2026-10-19", "2026-11-03", null);
        assertThat(file).contains("UID:hama-FIXED-" + id + "@hama.app", "DTSTART;TZID=Asia/Seoul:20261005T090000", "RRULE:" + rule);
        assertThat(file.split("BEGIN:VEVENT", -1)).hasSize(2);
        assertThat(file).doesNotContain("종료된 반복");
    }

    @Test
    void 요청_기간은_양끝포함_366일까지이며_잘못된_쿼리는_JSON_400이다() throws Exception {
        String token = signup();
        assertThat(query(token, "2024-01-01", "2024-12-31", null).isEmpty()).isTrue();
        for (String[] range : List.of(new String[]{"2024-01-01", "2025-01-01"},
                new String[]{"2026-10-02", "2026-10-01"}, new String[]{"2026-02-30", "2026-03-01"},
                new String[]{"2026-1-01", "2026-01-02"}, new String[]{"0999-12-31", "1000-01-01"})) {
            for (String path : List.of("/api/v1/calendar", "/api/v1/calendar/export")) {
                mvc.perform(auth(get(path).param("from", range[0]).param("to", range[1]), token))
                        .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.error.code").value("CALENDAR_INVALID_RANGE"));
            }
        }
        mvc.perform(auth(get("/api/v1/calendar/export").accept("text/calendar"), token))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("CALENDAR_INVALID_RANGE"));
        for (String types : List.of("", "FIXED,", "UNKNOWN", "fixed")) {
            mvc.perform(auth(get("/api/v1/calendar").param("from", "2026-10-01").param("to", "2026-10-02").param("types", types), token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("CALENDAR_INVALID_TYPES"));
        }
    }

    @Test
    void 두_API는_인증이_필요하고_ICS실패도_JSON이다() throws Exception {
        for (String path : List.of("/api/v1/calendar", "/api/v1/calendar/export")) {
            mvc.perform(get(path).param("from", "2026-10-01").param("to", "2026-10-01").accept("text/calendar"))
                    .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void 파일생성_오류도_공통_JSON과_traceId를_사용하고_상세원인을_숨긴다() throws Exception {
        String token = signup();
        doThrow(new IllegalStateException("private internal failure")).when(icsWriter).write(anyList());
        MvcResult result = mvc.perform(auth(get("/api/v1/calendar/export")
                        .param("from", "2026-10-01").param("to", "2026-10-01").accept("text/calendar"), token))
                .andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(header().exists("X-Trace-Id")).andReturn();
        assertThat(body(result).at("/error/message").asString()).doesNotContain("private");
        assertThat(body(result).at("/traceId").asString()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void 시간없는_TASK와_종일일정은_날짜형_ICS이고_9999년_마지막날도_지원한다() throws Exception {
        String token = signup();
        todo(token, "마지막 할 일", "9999-12-31", null, null);
        String maximum = export(token, "9999-12-31", "9999-12-31", "TASK");
        assertThat(maximum).contains("DTSTART;VALUE=DATE:99991231", "DURATION:P1D").doesNotContain("+10000");
        assertThat(query(token, "9999-12-31", "9999-12-31", "TASK").size()).isOne();
        schedule(token, "PERSONAL", "종일", "2026-10-03T00:00:00", "2026-10-05T00:00:00", true, null);
        String allDay = export(token, "2026-10-04", "2026-10-04", null);
        assertThat(allDay).contains("DTSTART;VALUE=DATE:20261003", "DTEND;VALUE=DATE:20261005").doesNotContain("VTIMEZONE");
    }

    @Test
    void OpenAPI는_JSON조회와_파일성공_JSON실패를_문서화한다() throws Exception {
        JsonNode document = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        JsonNode responses = document.get("paths").get("/api/v1/calendar/export").get("get").get("responses");
        assertThat(responses.get("200").get("content").has("text/calendar")).isTrue();
        assertThat(responses.get("400").get("content").has("application/json")).isTrue();
        assertThat(responses.get("401").get("content").has("application/json")).isTrue();
        assertThat(document.get("paths").get("/api/v1/calendar").get("get").get("parameters").valueStream()
                .filter(p -> p.get("name").asString().equals("from")).findFirst().orElseThrow().get("required").asBoolean()).isTrue();
    }

    @Test
    void 과거_시간대_전환으로_실제종료가_시작보다_이르면_일정을_거절한다() throws Exception {
        String token = signup();
        mvc.perform(auth(post("/api/v1/calendar/schedules").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"PERSONAL","title":"잘못된 실제 시간","startAt":"1988-05-08T02:30:00",
                        "endAt":"1988-05-08T03:00:00","allDay":false}
                        """), token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_INVALID_TIME"));
    }

    @Test
    void 정상_JSON조회에_파일Accept를_보내면_406_JSON이다() throws Exception {
        String token = signup();
        mvc.perform(auth(get("/api/v1/calendar").param("from", "2026-10-01").param("to", "2026-10-01")
                .accept("text/calendar"), token)).andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("CALENDAR_NOT_ACCEPTABLE"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void 결과상한_초과는_부분응답없이_400이며_ICS는_원본을_센다() throws Exception {
        String token = signup();
        long id = schedule(token, "FIXED", "상한", "2026-01-01T00:00:00", "2026-01-02T00:00:00", true,
                "count=101;freq=daily");
        assertThat(query(token, "2026-01-01", "2026-04-10", null).size()).isEqualTo(100);
        mvc.perform(auth(get("/api/v1/calendar").param("from", "2026-01-01").param("to", "2026-04-11"), token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("CALENDAR_RESULT_LIMIT_EXCEEDED"));
        assertThat(export(token, "2026-01-01", "2026-04-11", null)).contains("RRULE:FREQ=DAILY;COUNT=101");
        assertThat(body(mvc.perform(auth(get("/api/v1/calendar/schedules/" + id), token))
                .andExpect(status().isOk()).andReturn()).at("/data/repeatRule").asString()).isEqualTo("count=101;freq=daily");
    }

    private JsonNode query(String token, String from, String to, String types) throws Exception {
        var request = get("/api/v1/calendar").param("from", from).param("to", to);
        if (types != null) request.param("types", types);
        return body(mvc.perform(auth(request, token)).andExpect(status().isOk()).andReturn()).get("data").get("items");
    }

    private String export(String token, String from, String to, String types) throws Exception {
        var request = get("/api/v1/calendar/export").param("from", from).param("to", to).accept("text/calendar");
        if (types != null) request.param("types", types);
        MvcResult result = mvc.perform(auth(request, token)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"hama-" + from + "-" + to + ".ics\""))
                .andExpect(header().exists("X-Trace-Id")).andReturn();
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).replace("\r\n ", "");
    }

    private String signup() throws Exception {
        return body(mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@calendar.test",
                        "password", "password1234", "name", "캘린더"))))
                .andExpect(status().isOk()).andReturn()).at("/data/accessToken").asString();
    }

    private long schedule(String token, String type, String title, String start, String end, boolean allDay, String rule) throws Exception {
        var data = new java.util.HashMap<String, Object>(Map.of("type", type, "title", title, "startAt", start,
                "endAt", end, "allDay", allDay));
        if (rule != null) data.put("repeatRule", rule);
        return body(mvc.perform(auth(post("/api/v1/calendar/schedules").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(data)), token)).andExpect(status().isCreated()).andReturn()).at("/data/scheduleId").asLong();
    }

    private long todo(String token, String title, String date, String start, String end) throws Exception {
        var data = new java.util.HashMap<String, Object>(Map.of("category", "TASK", "content", title, "todoDate", date));
        if (start != null) { data.put("startTime", start); data.put("endTime", end); }
        return body(mvc.perform(auth(post("/api/v1/todos").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(data)), token)).andExpect(status().isCreated()).andReturn()).at("/data/todoId").asLong();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private JsonNode body(MvcResult result) {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
