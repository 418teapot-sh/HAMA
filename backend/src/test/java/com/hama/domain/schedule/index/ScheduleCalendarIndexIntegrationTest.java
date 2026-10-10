package com.hama.domain.schedule.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hama.domain.calendar.dto.CalendarQuery;
import com.hama.domain.calendar.service.CalendarReadRepository;
import com.hama.domain.calendar.service.CalendarService;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import com.hama.domain.schedule.repository.ScheduleRepository;
import com.hama.domain.shared.time.Be3Time;
import com.hama.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ScheduleCalendarIndexIntegrationTest {
    private static final AtomicLong USERS = new AtomicLong(8000000);
    private final long user = USERS.incrementAndGet();
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ScheduleCalendarIndex index;
    @Autowired private CalendarReadRepository reader;
    @Autowired private CalendarService calendar;
    @Autowired private ScheduleRepository schedules;
    @Autowired private PlatformTransactionManager transactions;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM schedule WHERE user_id = ?", user);
        jdbc.update("DELETE FROM todo WHERE user_id = ?", user);
    }

    @Test
    void 미보정_기존행의_원본과_감사시각을_보존하고_반복종료행은_DB에서_제외한다() {
        long id = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=DAILY;COUNT=2");
        Map<String, Object> original = original(id);
        var query = CalendarQuery.parse("2026-10-03", "2026-10-03", "FIXED");
        assertThat(reader.schedules(user, query, 0)).hasSize(1);
        assertThat(calendar.get(user, query).items()).isEmpty();
        assertThat(index.backfillBatch(id - 1).count()).isEqualTo(1);
        assertThat(original(id)).isEqualTo(original);
        assertThat(index.backfillBatch(id - 1).count()).isZero();
        assertThat(reader.schedules(user, query, 0)).isEmpty();
        assertThat(calendar.get(user, query).items()).isEmpty();
    }

    @Test
    void 감사시각이_같아도_원본만_바뀐_구버전수정은_누락하지않고_재보정한다() {
        long id = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=DAILY;COUNT=1");
        index.backfillBatch(id - 1);
        jdbc.update("UPDATE schedule SET repeat_rule = 'FREQ=DAILY' WHERE schedule_id = ?", id);
        var query = CalendarQuery.parse("2026-10-10", "2026-10-10", "FIXED");
        assertThat(calendar.get(user, query).items()).hasSize(1);
        index.backfillBatch(id - 1);
        assertThat(calendar.get(user, query).items()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT calendar_last_end_epoch_second FROM schedule WHERE schedule_id = ?", Long.class, id)).isNull();
    }

    @Test
    void 긴_발생의_꼬리는_COUNT와_UNTIL_이후에도_포함한다() {
        for (String rule : List.of("FREQ=DAILY;COUNT=2", "FREQ=DAILY;UNTIL=20261002T000000Z")) {
            long id = legacy("2026-10-01T09:00:00", "2026-10-04T10:00:00", false, rule);
            index.backfillBatch(id - 1);
        }
        var last = CalendarQuery.parse("2026-10-05", "2026-10-05", "FIXED");
        assertThat(calendar.get(user, last).items()).hasSize(2);
        assertThat(reader.schedules(user, CalendarQuery.parse("2026-10-06", "2026-10-06", "FIXED"), 0)).isEmpty();
    }

    @Test
    void 종일_종료자정은_제외하고_생성과_수정은_종료정보를_동기화한다() {
        Schedule schedule = schedules.saveAndFlush(Schedule.create(user, ScheduleType.FIXED, "종일",
                LocalDateTime.parse("2026-10-01T00:00:00"), LocalDateTime.parse("2026-10-02T00:00:00"), true,
                "FREQ=DAILY;COUNT=2", null));
        assertThat(schedule.getCalendarLastEndEpochSecond()).isEqualTo(LocalDateTime.parse("2026-10-03T00:00:00").atZone(Be3Time.KST).toEpochSecond());
        assertThat(reader.schedules(user, CalendarQuery.parse("2026-10-03", "2026-10-03", "FIXED"), 0)).isEmpty();
        schedule.revise(ScheduleType.FIXED, "종일", schedule.getStartAt(), schedule.getEndAt(), true, "FREQ=DAILY", null);
        schedules.saveAndFlush(schedule);
        assertThat(reader.schedules(user, CalendarQuery.parse("2026-10-03", "2026-10-03", "FIXED"), 0)).hasSize(1);
        assertThat(index.backfillBatch(schedule.getId() - 1).count()).isZero();
    }

    @Test
    void 백개넘는_미보정만료후보_뒤의_유효일정도_찾는다() {
        for (int i = 0; i < 205; i++) legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=DAILY;COUNT=1");
        long live = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=DAILY");
        var query = CalendarQuery.parse("2026-10-05", "2026-10-05", "FIXED");
        assertThat(calendar.get(user, query).items()).singleElement().satisfies(item -> assertThat(item.refId()).isEqualTo(live));
        assertThat(new String(calendar.export(user, query), StandardCharsets.UTF_8)).contains("hama-FIXED-" + live + "@hama.app");
    }

    @Test
    void 조회분할과_ICS원본은_각각_10000개까지이며_종류가_예산을_공유한다() {
        legacy("2026-01-01T00:00:00", "2026-01-02T00:00:00", true, "FREQ=DAILY;COUNT=101");
        var annual = CalendarQuery.parse("2026-01-01", "2026-04-11", null);
        assertThat(calendar.get(user, annual).items()).hasSize(101);
        assertThat(new String(calendar.export(user, annual), StandardCharsets.UTF_8))
                .contains("RRULE:FREQ=DAILY;COUNT=101");
        jdbc.update("DELETE FROM schedule WHERE user_id = ?", user);
        jdbc.batchUpdate("""
                INSERT INTO todo(user_id,category,content,todo_date,status,postponed_count,created_at,updated_at)
                VALUES (?, 'TASK', '테스트', '2026-10-05', 'PENDING', 0, NOW(), NOW())
                """, java.util.stream.IntStream.range(0, 10000).mapToObj(i -> new Object[]{user}).toList());
        var query = CalendarQuery.parse("2026-10-05", "2026-10-05", null);
        assertThat(calendar.get(user, query).items()).hasSize(10000);
        assertThat(new String(calendar.export(user, query), StandardCharsets.UTF_8).split("BEGIN:VEVENT", -1)).hasSize(10001);
        legacy("2026-10-05T09:00:00", "2026-10-05T10:00:00", false, null);
        assertLimit(() -> calendar.get(user, query));
        assertLimit(() -> calendar.export(user, query));
        assertThat(calendar.get(user, CalendarQuery.parse("2026-10-05", "2026-10-05", "FIXED")).items()).hasSize(1);
    }

    @Test
    void 여러날_분할결과도_10000개에서_중단하며_ICS는_원본만_센다() {
        for (int i = 0; i < 100; i++) {
            legacy("2026-01-01T00:00:00", "2026-04-11T00:00:00", true, null);
        }
        var query = CalendarQuery.parse("2026-01-01", "2026-04-10", null);
        assertThat(calendar.get(user, query).items()).hasSize(10000);
        legacy("2026-04-10T09:00:00", "2026-04-10T10:00:00", false, null);
        assertLimit(() -> calendar.get(user, query));
        assertThat(new String(calendar.export(user, query), StandardCharsets.UTF_8).split("BEGIN:VEVENT", -1)).hasSize(102);
    }

    @Test
    void 수천년짜리_매일반복도_10000개에서_중단한다() {
        legacy("1000-01-01T09:00:00", "9999-01-01T10:00:00", false, "FREQ=DAILY");
        assertTimeout(Duration.ofSeconds(3), () -> assertLimit(() -> calendar.get(user,
                CalendarQuery.parse("9000-01-01", "9000-12-31", "FIXED"))));
    }

    @Test
    void gap과_그레고리력절단_최소연도를_JDBC가_그대로_읽는다() {
        for (String[] dates : List.of(new String[]{"1988-05-08T02:30:00", "1988-05-08T03:45:00"},
                new String[]{"1582-10-10T09:00:00", "1582-10-10T10:00:00"},
                new String[]{"1000-01-01T00:00:00", "1000-01-01T00:30:00"})) {
            long id = legacy(dates[0], dates[1], false, "FREQ=DAILY;COUNT=1");
            index.backfillBatch(id - 1);
            String date = dates[0].substring(0, 10);
            assertThat(calendar.get(user, CalendarQuery.parse(date, date, "FIXED")).items()).hasSize(1);
        }
    }

    @Test
    void 실패행만_건너뛰고_앞뒤_정상행과_원본을_보존하며_다음기동에_재시도한다() {
        long good = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=DAILY;COUNT=1");
        long bad = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=YEARLY");
        long after = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, null);
        Map<String, Object> original = original(bad);
        var batch = index.backfillBatch(good - 1);
        assertThat(batch).isEqualTo(new ScheduleCalendarIndex.Batch(after, 3, 2, 1));
        for (long id : List.of(good, after)) {
            assertThat(jdbc.queryForObject("SELECT calendar_source_start_at FROM schedule WHERE schedule_id = ?", LocalDateTime.class, id)).isNotNull();
        }
        assertThat(original(bad)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT calendar_source_start_at FROM schedule WHERE schedule_id = ?", LocalDateTime.class, bad)).isNull();
        assertThat(index.backfillBatch(after).count()).isZero();
        assertThat(index.backfillBatch(good - 1).failed()).isEqualTo(1);
        jdbc.update("UPDATE schedule SET repeat_rule = 'FREQ=DAILY' WHERE schedule_id = ?", bad);
        assertThat(index.backfillBatch(good - 1).succeeded()).isEqualTo(1);
        assertThat(index.backfillBatch(good - 1).count()).isZero();
    }

    @Test
    void 외부트랜잭션이_롤백되어도_성공행은_독립커밋된다() {
        long good = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, null);
        long bad = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=YEARLY");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(index.backfillBatch(good - 1).succeeded()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT calendar_source_start_at FROM schedule WHERE schedule_id = ?", LocalDateTime.class, good)).isNotNull();
        assertThat(index.backfillBatch(good - 1)).isEqualTo(new ScheduleCalendarIndex.Batch(bad, 1, 0, 1));
    }

    @Test
    void UPDATE이후_실패해도_그행만_롤백하고_다음행을_커밋한다() {
        long bad = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, null);
        long good = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, null);
        JdbcTemplate failing = spy(jdbc);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            Object[] arguments = invocation.getArguments();
            if (arguments[arguments.length - 1].equals(bad)) {
                throw new org.springframework.dao.DataIntegrityViolationException("private-value");
            }
            return result;
        }).when(failing).update(startsWith("UPDATE schedule SET calendar_last_end_epoch_second"), any(Object[].class));
        var isolated = new ScheduleCalendarIndex(failing, transactions);
        assertThat(isolated.backfillBatch(bad - 1)).isEqualTo(new ScheduleCalendarIndex.Batch(good, 2, 1, 1));
        assertThat(jdbc.queryForObject("SELECT calendar_source_start_at FROM schedule WHERE schedule_id = ?", LocalDateTime.class, bad)).isNull();
        assertThat(jdbc.queryForObject("SELECT calendar_source_start_at FROM schedule WHERE schedule_id = ?", LocalDateTime.class, good)).isNotNull();
    }

    @Test
    void 전체실패_배치도_커서가_전진하고_후속배치를_처리한다() {
        long first = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=YEARLY");
        for (int i = 1; i < 100; i++) legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=YEARLY");
        long good = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, null);
        var failed = index.backfillBatch(first - 1);
        assertThat(failed.count()).isEqualTo(100);
        assertThat(failed.failed()).isEqualTo(100);
        assertThat(index.backfillBatch(failed.lastId())).isEqualTo(new ScheduleCalendarIndex.Batch(good, 1, 1, 0));
        assertTimeout(Duration.ofSeconds(5), () -> new ScheduleCalendarBackfill(index).run(null));
    }

    @Test
    void 후보선정후_동시에_삭제된_행은_다시_보정하지않는다() throws Exception {
        long id = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, null);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch selected = new CountDownLatch(1);
        JdbcTemplate observed = spy(jdbc);
        doAnswer(invocation -> {
            Object ids = invocation.callRealMethod();
            selected.countDown();
            return ids;
        }).when(observed).queryForList(anyString(), eq(Long.class), any(Object[].class));
        var isolated = new ScheduleCalendarIndex(observed, transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT schedule_id FROM schedule WHERE schedule_id = ? FOR UPDATE", Long.class, id);
                locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test lock timeout");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                jdbc.update("UPDATE schedule SET deleted_at = NOW() WHERE schedule_id = ?", id);
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var backfill = executor.submit(() -> isolated.backfillBatch(id - 1));
            assertThat(selected.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            deletion.get(10, TimeUnit.SECONDS);
            assertThat(backfill.get(10, TimeUnit.SECONDS).succeeded()).isZero();
        } finally { release.countDown(); }
        assertThat(jdbc.queryForObject("SELECT calendar_source_start_at FROM schedule WHERE schedule_id = ?", LocalDateTime.class, id)).isNull();
    }

    @Test
    void 수정행의_잠금이_풀린후_최신원본으로_보정한다() throws Exception {
        long id = legacy("2026-10-01T09:00:00", "2026-10-01T10:00:00", false, "FREQ=DAILY;COUNT=1");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("SELECT schedule_id FROM schedule WHERE schedule_id = ? FOR UPDATE", Long.class, id);
                locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test lock timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                jdbc.update("UPDATE schedule SET repeat_rule = 'FREQ=DAILY' WHERE schedule_id = ?", id);
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var backfill = executor.submit(() -> index.backfillBatch(id - 1));
            release.countDown();
            update.get(10, TimeUnit.SECONDS);
            backfill.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT calendar_source_repeat_rule FROM schedule WHERE schedule_id = ?", String.class, id)).isEqualTo("FREQ=DAILY");
        assertThat(calendar.get(user, CalendarQuery.parse("2026-10-10", "2026-10-10", null)).items()).hasSize(1);
    }

    private long legacy(String start, String end, boolean allDay, String rule) {
        jdbc.update("""
                INSERT INTO schedule(user_id,type,title,start_at,end_at,all_day,repeat_rule,memo,created_at,updated_at)
                VALUES (?, 'FIXED', '기존 일정', ?, ?, ?, ?, '원본 메모', '2026-01-01 00:00:00', '2026-01-02 00:00:00')
                """, user, LocalDateTime.parse(start), LocalDateTime.parse(end), allDay, rule);
        return jdbc.queryForObject("SELECT MAX(schedule_id) FROM schedule WHERE user_id = ?", Long.class, user);
    }

    private void task(String date) {
        jdbc.update("""
                INSERT INTO todo(user_id,category,content,todo_date,status,postponed_count,created_at,updated_at)
                VALUES (?, 'TASK', '테스트', ?, 'PENDING', 0, NOW(), NOW())
                """, user, date);
    }

    private Map<String, Object> original(long id) {
        return jdbc.queryForMap("""
                SELECT user_id,type,title,start_at,end_at,all_day,repeat_rule,memo,created_at,updated_at,deleted_at
                FROM schedule WHERE schedule_id = ?
                """, id);
    }

    private void assertLimit(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode().name()).isEqualTo("CALENDAR_RESULT_LIMIT_EXCEEDED"));
    }
}
