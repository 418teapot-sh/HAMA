package com.hama.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.goalai.GoalAiTestSupport;
import com.hama.domain.goalai.service.BusyTimeReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class ScheduleCompletionApiIntegrationTest extends GoalAiTestSupport {
    private static final String BASE = "/api/v1/calendar/schedules";
    private static final String DATE = "2026-10-05";
    private static final String START = DATE + "T09:00:00";

    @Autowired
    private BusyTimeReader busyTimeReader;

    @Test
    void 개인과_고정일정을_실제JWT로_완료하고_취소한다() throws Exception {
        User owner = user();
        for (String type : List.of("PERSONAL", "FIXED")) {
            long id = schedule(owner, type, START, DATE + "T10:00:00", false, null);
            assertState(owner, id, DATE, START, "PENDING");
            change(owner, id, "complete", DATE, START, 200);
            assertState(owner, id, DATE, START, "COMPLETED");
            change(owner, id, "complete", DATE, START, 409);
            change(owner, id, "uncomplete", DATE, START, 200);
            assertState(owner, id, DATE, START, "PENDING");
            change(owner, id, "uncomplete", DATE, START, 409);
            change(owner, id, "complete", DATE, START, 200);
            assertState(owner, id, DATE, START, "COMPLETED");
        }
    }

    @Test
    void 소유권과_인증을_검사하고_삭제된_일정은_완료할수없다() throws Exception {
        User owner = user();
        User other = user();
        long id = schedule(owner, "PERSONAL", START, DATE + "T10:00:00", false, null);
        for (String action : List.of("complete", "uncomplete")) {
            change(null, id, action, DATE, START, 401);
            change(other, id, action, DATE, START, 403);
            change(owner, Long.MAX_VALUE, action, DATE, START, 404);
        }
        change(owner, id, "complete", DATE, START, 200);
        call(delete(BASE + "/" + id), owner, null, 200);
        change(owner, id, "complete", DATE, START, 404);
        change(owner, id, "uncomplete", DATE, START, 404);
        assertThat(items(owner, DATE, DATE)).isEmpty();
        assertThat(items(other, DATE, DATE)).isEmpty();
    }

    @Test
    void 실제발생하지_않는_날짜와_시작시각_누락입력은_거절한다() throws Exception {
        User owner = user();
        long id = schedule(owner, "FIXED", START, DATE + "T10:00:00", false, "FREQ=WEEKLY;COUNT=2");
        change(owner, id, "complete", "2026-10-06", START, 400);
        change(owner, id, "complete", DATE, DATE + "T09:01:00", 400);
        change(owner, id, "complete", "2026-10-19", "2026-10-19T09:00:00", 400);
        for (Map<String, String> body : List.of(Map.<String, String>of(), Map.of("date", DATE),
                Map.of("occurrenceStartAt", START))) {
            call(patch(BASE + "/" + id + "/complete"), owner, body, 400);
        }
        assertState(owner, id, DATE, START, "PENDING");
    }

    @Test
    void 여러날_시간일정과_종일일정은_표시날짜별로_독립적으로_체크한다() throws Exception {
        User owner = user();
        for (boolean allDay : List.of(false, true)) {
            String start = allDay ? DATE + "T00:00:00" : DATE + "T23:00:00";
            String end = allDay ? "2026-10-07T00:00:00" : "2026-10-06T01:00:00";
            long id = schedule(owner, "PERSONAL", start, end, allDay, null);
            change(owner, id, "complete", "2026-10-06", start, 200);
            assertState(owner, id, DATE, start, "PENDING");
            assertState(owner, id, "2026-10-06", start, "COMPLETED");
            change(owner, id, "complete", DATE, start, 200);
            change(owner, id, "uncomplete", "2026-10-06", start, 200);
            assertState(owner, id, DATE, start, "COMPLETED");
            assertState(owner, id, "2026-10-06", start, "PENDING");
            change(owner, id, "complete", "2026-10-07", start, 400);
        }
    }

    @Test
    void 같은날_겹치는_반복발생은_원래_발생시작시각으로_구분한다() throws Exception {
        User owner = user();
        long id = schedule(owner, "FIXED", START, "2026-10-07T10:00:00", false, "FREQ=DAILY;COUNT=3");
        String second = "2026-10-06T09:00:00";
        change(owner, id, "complete", "2026-10-06", START, 200);
        assertState(owner, id, "2026-10-06", START, "COMPLETED");
        assertState(owner, id, "2026-10-06", second, "PENDING");
        assertState(owner, id, "2026-10-07", START, "PENDING");
        change(owner, id, "complete", "2026-10-06", second, 200);
        change(owner, id, "uncomplete", "2026-10-06", START, 200);
        assertState(owner, id, "2026-10-06", START, "PENDING");
        assertState(owner, id, "2026-10-06", second, "COMPLETED");
        change(owner, id, "complete", DATE, second, 400);
    }

    @Test
    void 제목메모와_같은날_시간변경은_보존하고_반복변경은_완료를_초기화한다() throws Exception {
        User owner = user();
        long id = schedule(owner, "PERSONAL", START, DATE + "T10:00:00", false, "FREQ=DAILY;COUNT=3");
        change(owner, id, "complete", DATE, START, 200);
        change(owner, id, "complete", "2026-10-06", "2026-10-06T09:00:00", 200);
        call(patch(BASE + "/" + id), owner, Map.of("title", "수정 제목", "memo", "수정 메모"), 200);
        assertState(owner, id, DATE, START, "COMPLETED");
        call(patch(BASE + "/" + id), owner,
                Map.of("startAt", DATE + "T10:00:00", "endAt", DATE + "T11:00:00"), 200);
        assertState(owner, id, DATE, DATE + "T10:00:00", "COMPLETED");
        assertState(owner, id, "2026-10-06", "2026-10-06T10:00:00", "COMPLETED");
        call(patch(BASE + "/" + id), owner, Map.of("repeatRule", "FREQ=DAILY;COUNT=4"), 200);
        assertState(owner, id, DATE, DATE + "T10:00:00", "PENDING");
        change(owner, id, "complete", DATE, DATE + "T10:00:00", 200);
        call(patch(BASE + "/" + id), owner,
                Map.of("startAt", "2026-10-06T10:00:00", "endAt", "2026-10-06T11:00:00"), 200);
        assertState(owner, id, "2026-10-06", "2026-10-06T10:00:00", "PENDING");
    }

    @Test
    void 완료된_개인일정은_체크해제후_투두로_전환한다() throws Exception {
        User owner = user();
        long id = schedule(owner, "PERSONAL", START, "2026-10-06T10:00:00", false, null);
        Map<String, Object> conversion = Map.of("sourceType", "PERSONAL", "refId", id,
                "targetType", "TASK", "todoDate", DATE);
        change(owner, id, "complete", "2026-10-06", START, 200);
        call(post("/api/v1/calendar/category-conversions"), owner, conversion, 409);
        change(owner, id, "uncomplete", "2026-10-06", START, 200);
        JsonNode converted = call(post("/api/v1/calendar/category-conversions"), owner, conversion, 200);
        assertThat(converted.at("/data/type").asString()).isEqualTo("TASK");
        call(get(BASE + "/" + id), owner, null, 404);
    }

    @Test
    void 완료해도_AI점유시간과_ICS의_UID및_반복규칙은_유지한다() throws Exception {
        User owner = user();
        long id = schedule(owner, "FIXED", START, DATE + "T10:00:00", false, "FREQ=DAILY;COUNT=3");
        LocalDate from = LocalDate.parse(DATE);
        LocalDate to = from.plusDays(2);
        var before = busyTimeReader.read(owner.id(), from, to, Set.of());
        String file = export(owner);
        assertThat(before.get(from)).isNotEmpty();
        change(owner, id, "complete", DATE, START, 200);
        assertThat(busyTimeReader.read(owner.id(), from, to, Set.of())).isEqualTo(before);
        assertThat(export(owner)).isEqualTo(file).contains("UID:hama-", "RRULE:FREQ=DAILY;COUNT=3");
        change(owner, id, "uncomplete", DATE, START, 200);
        assertThat(busyTimeReader.read(owner.id(), from, to, Set.of())).isEqualTo(before);
    }

    @Test
    void 동시완료는_한번만_성공하고_하나의_완료상태로_조회된다() throws Exception {
        User owner = user();
        long id = schedule(owner, "PERSONAL", START, DATE + "T10:00:00", false, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
                }
                return mvc.perform(patch(BASE + "/" + id + "/complete")
                                .header("Authorization", "Bearer " + owner.token())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("date", DATE, "occurrenceStartAt", START))))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertState(owner, id, DATE, START, "COMPLETED");
        change(owner, id, "uncomplete", DATE, START, 200);
        assertState(owner, id, DATE, START, "PENDING");
    }

    @Test
    void 회원탈퇴는_본인_일정완료만_지우고_다른사람_기록은_유지한다() throws Exception {
        User owner = user();
        User other = user();
        long id = schedule(owner, "PERSONAL", START, DATE + "T10:00:00", false, null);
        long otherId = schedule(other, "FIXED", START, DATE + "T10:00:00", false, null);
        change(owner, id, "complete", DATE, START, 200);
        change(other, otherId, "complete", DATE, START, 200);
        call(post("/api/v1/users/me/withdraw"), owner, Map.of("password", "password1234!"), 200);
        assertThat(jdbc.queryForObject("select count(*) from schedule_completion where user_id = ?", Integer.class, owner.id())).isZero();
        assertState(other, otherId, DATE, START, "COMPLETED");
    }

    @Test
    void 완료와_회원탈퇴가_경합해도_탈퇴후_완료기록이_남지않는다() throws Exception {
        User owner = user();
        long id = schedule(owner, "PERSONAL", START, DATE + "T10:00:00", false, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var complete = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과");
                return mvc.perform(patch(BASE + "/" + id + "/complete")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("date", DATE, "occurrenceStartAt", START))))
                        .andReturn().getResponse().getStatus();
            });
            var withdraw = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과");
                return mvc.perform(post("/api/v1/users/me/withdraw")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", "password1234!"))))
                        .andReturn().getResponse().getStatus();
            });
            try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            assertThat(withdraw.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(complete.get(15, TimeUnit.SECONDS)).isIn(200, 401, 404);
        }
        assertThat(jdbc.queryForObject("select count(*) from schedule_completion where user_id = ?", Integer.class, owner.id())).isZero();
    }

    @Test
    void 날짜수정과_완료가_경합해도_변경된_날짜에_이전_체크가_남지않는다() throws Exception {
        User owner = user();
        long id = schedule(owner, "PERSONAL", START, DATE + "T10:00:00", false, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var complete = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과");
                return mvc.perform(patch(BASE + "/" + id + "/complete")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("date", DATE, "occurrenceStartAt", START))))
                        .andReturn().getResponse().getStatus();
            });
            var update = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과");
                return mvc.perform(patch(BASE + "/" + id)
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("startAt", "2026-10-06T09:00:00",
                                "endAt", "2026-10-06T10:00:00"))))
                        .andReturn().getResponse().getStatus();
            });
            try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            assertThat(update.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(complete.get(15, TimeUnit.SECONDS)).isIn(200, 400);
        }
        assertState(owner, id, "2026-10-06", "2026-10-06T09:00:00", "PENDING");
        assertThat(jdbc.queryForObject("select count(*) from schedule_completion where schedule_id = ?", Integer.class, id)).isZero();
    }

    private long schedule(User owner, String type, String start, String end, boolean allDay, String rule)
            throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("type", type, "title", "완료 검증",
                "startAt", start, "endAt", end, "allDay", allDay));
        if (rule != null) {
            body.put("repeatRule", rule);
        }
        return call(post(BASE), owner, body, 201).at("/data/scheduleId").asLong();
    }

    private void change(User owner, long id, String action, String date, String occurrence, int expected)
            throws Exception {
        String occurrenceStartAt = occurrence;
        if (expected == 200) {
            occurrenceStartAt = items(owner, date, date).stream()
                    .filter(item -> item.get("refId").asLong() == id)
                    .filter(item -> occurrence.equals(item.get("occurrenceStartAt").asString()))
                    .findFirst().orElseThrow().get("occurrenceStartAt").asString();
        }
        call(patch(BASE + "/" + id + "/" + action), owner,
                Map.of("date", date, "occurrenceStartAt", occurrenceStartAt), expected);
    }

    private List<JsonNode> items(User owner, String from, String to) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        call(get("/api/v1/calendar").param("from", from).param("to", to), owner, null, 200)
                .at("/data/items").forEach(result::add);
        return result;
    }

    private void assertState(User owner, long id, String date, String occurrence, String state) throws Exception {
        var matching = items(owner, date, date).stream()
                .filter(item -> item.get("refId").asLong() == id)
                .filter(item -> item.get("occurrenceStartAt").asString().equals(occurrence)).toList();
        assertThat(matching).hasSize(1);
        assertThat(matching.getFirst().get("date").asString()).isEqualTo(date);
        assertThat(matching.getFirst().get("status").asString()).isEqualTo(state);
    }

    private String export(User owner) throws Exception {
        return mvc.perform(get("/api/v1/calendar/export").param("from", DATE).param("to", "2026-10-07")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
