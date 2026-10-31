package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.hama.domain.calendar.google.importer.GoogleCalendarEvent;
import com.hama.domain.calendar.google.importer.GoogleEventsProvider;
import com.hama.domain.calendar.google.oauth.GoogleCalendarAccount;
import com.hama.domain.calendar.google.oauth.GoogleCalendarOAuthProvider;
import com.hama.domain.goalai.service.BusyTimeReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

@TestPropertySource(properties = {
        "GOOGLE_CALENDAR_CLIENT_ID=test-client", "GOOGLE_CALENDAR_CLIENT_SECRET=test-secret",
        "GOOGLE_CALENDAR_REDIRECT_URI=http://localhost:5173/google-callback",
        "GOOGLE_CALENDAR_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GoogleCalendarImportApiIntegrationTest extends GoalAiTestSupport {
    @MockitoBean private GoogleCalendarOAuthProvider google;
    @MockitoBean private GoogleEventsProvider events;
    @Autowired private BusyTimeReader busyTimes;
    private static final String BASE = "/api/v1/calendar/import/google";

    @BeforeEach
    void configureGoogle() {
        when(google.exchange(anyString(), anyString())).thenReturn(new GoogleCalendarOAuthProvider.Token("test-token", 3600));
        when(google.calendars(anyString())).thenReturn(List.of(new GoogleCalendarAccount("calendar@example.test", "테스트", "Asia/Seoul", true, "owner")));
        when(events.fetchEvents(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(new GoogleCalendarEvent("event-1", "confirmed", "회의", "메모",
                        new GoogleCalendarEvent.EventTime(null, "2026-10-02T00:00:00Z"),
                        new GoogleCalendarEvent.EventTime(null, "2026-10-02T01:00:00Z"))));
    }

    private String authorized(User user) throws Exception {
        JsonNode start = call(post(BASE + "/authorization"), user, null, 200).get("data");
        String session = start.get("sessionId").asString();
        String state = java.util.Arrays.stream(URI.create(start.get("authorizationUrl").asString()).getRawQuery().split("&"))
                .filter(value -> value.startsWith("state=")).map(value -> URLDecoder.decode(value.substring(6), StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
        call(post(BASE + "/authorization/complete"), user, Map.of("sessionId", session, "state", state, "code", "fake-code"), 200);
        return session;
    }

    private JsonNode runImport(User user, String session, int status) throws Exception {
        return call(post(BASE + "/" + session + "/import"), user,
                Map.of("calendarId", "calendar@example.test", "from", "2026-10-01", "to", "2026-10-03"), status);
    }

    @Test
    void 실제_API_흐름에서_중복과_분류전환_삭제후_재가져오기를_처리한다() throws Exception {
        User user = user();
        JsonNode first = runImport(user, authorized(user), 200);
        assertThat(first.at("/data/importedCount").asInt()).isEqualTo(1);
        Long schedule = jdbc.queryForObject("select schedule_id from schedule where user_id=? and deleted_at is null", Long.class, user.id());
        assertThat(jdbc.queryForObject("select start_at from schedule where schedule_id=?", java.sql.Timestamp.class, schedule)
                .toLocalDateTime().toString()).isEqualTo("2026-10-02T09:00");
        assertThat(busyTimes.read(user.id(), LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-02"), Set.of())
                .get(LocalDate.parse("2026-10-02"))).anyMatch(interval -> interval.start() == 540 && interval.end() == 600);
        assertThat(runImport(user, authorized(user), 200).at("/data/duplicateCount").asInt()).isEqualTo(1);
        JsonNode converted = call(post("/api/v1/calendar/category-conversions"), user,
                Map.of("sourceType", "PERSONAL", "targetType", "TASK", "refId", schedule, "todoDate", "2026-10-02"), 200);
        assertThat(runImport(user, authorized(user), 200).at("/data/duplicateCount").asInt()).isEqualTo(1);
        long todoId = converted.at("/data/refId").asLong();
        call(delete("/api/v1/todos/" + todoId), user, null, 200);
        assertThat(runImport(user, authorized(user), 200).at("/data/importedCount").asInt()).isEqualTo(1);
    }

    @Test
    void 다른_사용자의_세션과_허용되지_않은_캘린더를_거부한다() throws Exception {
        User owner = user(); User other = user(); String session = authorized(owner);
        call(get(BASE + "/" + session + "/calendars"), other, null, 403);
        call(post(BASE + "/" + session + "/import"), owner,
                Map.of("calendarId", "other@example.test", "from", "2026-10-01", "to", "2026-10-03"), 403);
        assertThat(jdbc.queryForObject("select count(*) from schedule where user_id=?", Long.class, owner.id())).isZero();
    }

    @Test
    void 탈퇴한_사용자의_유효한_JWT로_새_세션을_만들지_않는다() throws Exception {
        User owner = user();
        call(post("/api/v1/users/me/withdraw"), owner, Map.of("password", "password1234!"), 200);
        var response = call(post(BASE + "/authorization"), owner, null, 404);
        assertThat(response.at("/error/code").asString()).isEqualTo("USER_NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from google_calendar_import_session where user_id=?", Long.class, owner.id())).isZero();
    }

    @Test
    void 천개_초과면_아무것도_저장하지_않고_세션을_보존한다() throws Exception {
        User owner = user(); String session = authorized(owner);
        var item = new GoogleCalendarEvent("id", "confirmed", "일정", null,
                new GoogleCalendarEvent.EventTime("2026-10-02", null),
                new GoogleCalendarEvent.EventTime("2026-10-03", null));
        when(events.fetchEvents(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(java.util.Collections.nCopies(1001, item));
        assertThat(runImport(owner, session, 400).at("/error/code").asString()).isEqualTo("GOOGLE_IMPORT_LIMIT_EXCEEDED");
        assertThat(jdbc.queryForObject("select count(*) from schedule where user_id=?", Long.class, owner.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from google_calendar_import_session where session_id=?", Long.class, session)).isOne();
    }

    @Test
    void 두_세션의_동시_가져오기에도_일정은_한번만_생성된다() throws Exception {
        User owner = user(); String first = authorized(owner); String second = authorized(owner);
        var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> { start.await(); return runImport(owner, first, 200); });
            var right = executor.submit(() -> { start.await(); return runImport(owner, second, 200); });
            start.countDown();
            int imported = left.get(20, TimeUnit.SECONDS).at("/data/importedCount").asInt()
                    + right.get(20, TimeUnit.SECONDS).at("/data/importedCount").asInt();
            assertThat(imported).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from schedule where user_id=? and deleted_at is null", Long.class, owner.id())).isOne();
        } finally { executor.shutdownNow(); }
    }

    @Test
    void 가져오기_외부조회_중_탈퇴하면_일정이_다시_생기지_않는다() throws Exception {
        User owner = user(); String session = authorized(owner);
        var reading = new CountDownLatch(1); var continueReading = new CountDownLatch(1);
        when(events.fetchEvents(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    reading.countDown();
                    if (!continueReading.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                    return List.of(new GoogleCalendarEvent("race", "confirmed", "일정", null,
                            new GoogleCalendarEvent.EventTime("2026-10-02", null),
                            new GoogleCalendarEvent.EventTime("2026-10-03", null)));
                });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(() -> runImport(owner, session, 404));
            assertThat(reading.await(10, TimeUnit.SECONDS)).isTrue();
            call(post("/api/v1/users/me/withdraw"), owner, Map.of("password", "password1234!"), 200);
            continueReading.countDown();
            assertThat(pending.get(20, TimeUnit.SECONDS).at("/error/code").asString()).isEqualTo("USER_NOT_FOUND");
            assertThat(jdbc.queryForObject("select count(*) from schedule where user_id=?", Long.class, owner.id())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from google_calendar_import_source where user_id=?", Long.class, owner.id())).isZero();
        } finally { continueReading.countDown(); executor.shutdownNow(); }
    }
}
