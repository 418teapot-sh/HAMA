package com.hama.domain.calendar.service;

import com.hama.domain.calendar.dto.CalendarQuery;
import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.schedule.index.ScheduleCalendarIndex;
import com.hama.domain.schedule.recurrence.ScheduleRecurrence;
import com.hama.domain.shared.time.Be3Time;
import com.hama.domain.todo.entity.TodoStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 엔티티를 영속성 컨텍스트에 누적하지 않고 필요한 원본을 ID 순서로 조금씩 읽습니다. */
@Repository
@RequiredArgsConstructor
public class CalendarReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public record ScheduleCandidate(long id, CalendarType type, String title,
            ScheduleRecurrence.Source source, String memo) {
        public CalendarEvent event() {
            return new CalendarEvent(type, id, title, source.startAt(), source.endAt(), source.allDay(), source.repeatRule(), memo);
        }
    }

    public record TaskCandidate(long id, String title, LocalDate date, LocalTime startTime,
            LocalTime endTime, TodoStatus status, String memo) {
        public CalendarEvent event() {
            boolean allDay = startTime == null;
            LocalDateTime start = allDay ? date.atStartOfDay() : date.atTime(startTime);
            LocalDateTime end = allDay ? start.plusDays(1) : date.atTime(endTime);
            return new CalendarEvent(CalendarType.TASK, id, title, start, end, allDay, null, memo);
        }
    }

    public List<ScheduleCandidate> schedules(long userId, CalendarQuery query, long afterId) {
        List<String> types = query.types().stream().filter(type -> type == CalendarType.FIXED || type == CalendarType.PERSONAL)
                .map(Enum::name).toList();
        if (types.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT schedule_id, type, title, start_at, end_at, all_day, repeat_rule, memo
                FROM schedule WHERE user_id = :user AND deleted_at IS NULL AND type IN (:types)
                  AND schedule_id > :cursor AND start_at <= :last
                  AND (end_at > :first OR repeat_rule IS NOT NULL)
                  AND (NOT (
                """ + ScheduleCalendarIndex.CURRENT + """
                  ) OR calendar_last_end_epoch_second IS NULL OR calendar_last_end_epoch_second > :epoch)
                ORDER BY schedule_id LIMIT 100
                """, Map.of("user", userId, "types", types, "cursor", afterId,
                        "first", query.from().atStartOfDay(), "last", query.to().atTime(23, 59, 59),
                        "epoch", query.from().atStartOfDay(Be3Time.KST).toEpochSecond()),
                (rs, row) -> new ScheduleCandidate(rs.getLong("schedule_id"), CalendarType.valueOf(rs.getString("type")),
                        rs.getString("title"), new ScheduleRecurrence.Source(rs.getObject("start_at", java.time.LocalDateTime.class),
                        rs.getObject("end_at", java.time.LocalDateTime.class), rs.getBoolean("all_day"), rs.getString("repeat_rule")), rs.getString("memo")));
    }

    public List<TaskCandidate> tasks(long userId, CalendarQuery query, long afterId) {
        if (!query.types().contains(CalendarType.TASK)) {
            return List.of();
        }
        return jdbc.query("""
                SELECT todo_id, content, todo_date, start_time, end_time, status, status_note
                FROM todo WHERE user_id = :user AND deleted_at IS NULL AND category = 'TASK'
                  AND todo_id > :cursor AND todo_date BETWEEN :first AND :last
                ORDER BY todo_id LIMIT 100
                """, Map.of("user", userId, "cursor", afterId, "first", query.from(), "last", query.to()),
                (rs, row) -> new TaskCandidate(rs.getLong("todo_id"), rs.getString("content"), rs.getObject("todo_date", LocalDate.class),
                        rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class),
                        TodoStatus.valueOf(rs.getString("status")), rs.getString("status_note")));
    }
}
