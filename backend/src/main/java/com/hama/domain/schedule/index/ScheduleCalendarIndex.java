package com.hama.domain.schedule.index;

import com.hama.domain.schedule.recurrence.ScheduleRecurrence;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
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
    }

    public record Batch(long lastId, int count) {
    }

    private record Row(long id, ScheduleRecurrence.Source source) {
    }

    /** 행 잠금과 파생값 갱신을 동일한 짧은 트랜잭션에서 수행합니다. 실패 배치는 원자적으로 롤백됩니다. */
    public Batch backfillBatch(long afterId) {
        return transaction.execute(status -> {
            List<Row> rows = jdbc.query("""
                    SELECT schedule_id, start_at, end_at, all_day, repeat_rule
                    FROM schedule WHERE deleted_at IS NULL AND schedule_id > ? AND NOT (
                    """ + CURRENT + ") ORDER BY schedule_id LIMIT 100 FOR UPDATE",
                    (rs, number) -> new Row(rs.getLong("schedule_id"), new ScheduleRecurrence.Source(
                            rs.getObject("start_at", java.time.LocalDateTime.class), rs.getObject("end_at", java.time.LocalDateTime.class),
                            rs.getBoolean("all_day"), rs.getString("repeat_rule"))), afterId);
            for (Row row : rows) {
                try {
                    Long end = recurrence.lastEndEpochSecond(row.source());
                    jdbc.update("""
                            UPDATE schedule SET calendar_last_end_epoch_second = ?,
                                calendar_source_start_at = start_at, calendar_source_end_at = end_at,
                                calendar_source_all_day = all_day, calendar_source_repeat_rule = repeat_rule
                            WHERE schedule_id = ?
                            """, end, row.id());
                } catch (RuntimeException exception) {
                    log.error("Schedule calendar index backfill failed: scheduleId={}", row.id());
                    throw new IllegalStateException("Schedule calendar index backfill failed: scheduleId=" + row.id(), exception);
                }
            }
            return new Batch(rows.isEmpty() ? afterId : rows.getLast().id(), rows.size());
        });
    }
}
