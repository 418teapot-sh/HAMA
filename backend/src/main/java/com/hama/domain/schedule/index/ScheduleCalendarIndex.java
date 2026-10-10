package com.hama.domain.schedule.index;

import com.hama.domain.schedule.recurrence.ScheduleRecurrence;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 원본 변경을 snapshot으로 감지합니다. 보정은 원본 및 auditing 컬럼을 건드리지 않습니다. */
@Component
@Slf4j
public class ScheduleCalendarIndex {
    public static final String CURRENT = """
            (calendar_source_start_at <=> start_at)
            AND (calendar_source_end_at <=> end_at)
            AND (calendar_source_all_day <=> all_day)
            AND (BINARY calendar_source_repeat_rule <=> BINARY repeat_rule)
            """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ScheduleRecurrence recurrence = new ScheduleRecurrence();

    public ScheduleCalendarIndex(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Batch(long lastId, int count, int succeeded, int failed) {
    }

    private record Row(long id, ScheduleRecurrence.Source source) {
    }

    /** ID 커서는 실패 행도 지나갑니다. 실패 행은 원본 snapshot을 바꾸지 않아 다음 기동에 재시도합니다. */
    public Batch backfillBatch(long afterId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT schedule_id FROM schedule
                WHERE deleted_at IS NULL AND schedule_id > ? AND NOT (
                """ + CURRENT + ") ORDER BY schedule_id LIMIT 100", Long.class, afterId);
        int succeeded = 0;
        int failed = 0;
        for (long id : ids) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> backfillRow(id)))) succeeded++;
            } catch (RuntimeException exception) {
                failed++;
                // SQL 예외 메시지에는 원본 값이 포함될 수 있으므로 내용 대신 종류만 기록합니다.
                log.error("Schedule calendar index backfill failed: scheduleId={}, errorType={}",
                        id, exception.getClass().getSimpleName());
            }
        }
        return new Batch(ids.isEmpty() ? afterId : ids.getLast(), ids.size(), succeeded, failed);
    }

    private boolean backfillRow(long id) {
        List<Row> rows = jdbc.query("""
                SELECT schedule_id, start_at, end_at, all_day, repeat_rule
                FROM schedule WHERE schedule_id = ? AND deleted_at IS NULL AND NOT (
                """ + CURRENT + ") FOR UPDATE",
                (rs, number) -> new Row(rs.getLong("schedule_id"), new ScheduleRecurrence.Source(
                        rs.getObject("start_at", java.time.LocalDateTime.class), rs.getObject("end_at", java.time.LocalDateTime.class),
                        rs.getBoolean("all_day"), rs.getString("repeat_rule"))), id);
        if (rows.isEmpty()) return false;
        Row row = rows.getFirst();
        Long end = recurrence.lastEndEpochSecond(row.source());
        jdbc.update("""
                UPDATE schedule SET calendar_last_end_epoch_second = ?,
                    calendar_source_start_at = start_at, calendar_source_end_at = end_at,
                    calendar_source_all_day = all_day, calendar_source_repeat_rule = repeat_rule
                WHERE schedule_id = ?
                """, end, row.id());
        return true;
    }
}
