package com.hama.domain.calendar.service;

import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import java.time.LocalDateTime;

/** ICS용 원본 일정. 반복 발생 건으로 분리하지 않아 UID와 RRULE이 보존됩니다. */
public record CalendarEvent(CalendarType type, Long refId, String title, LocalDateTime startAt,
        LocalDateTime endAt, boolean allDay, String repeatRule, String memo, String calendarUid) {

    public static CalendarEvent from(Schedule schedule) {
        return new CalendarEvent(CalendarType.valueOf(schedule.getType().name()), schedule.getId(),
                schedule.getTitle(), schedule.getStartAt(), schedule.getEndAt(), schedule.isAllDay(),
                schedule.getRepeatRule(), schedule.getMemo(), schedule.getCalendarUid());
    }

    public static CalendarEvent from(Todo todo) {
        boolean allDay = todo.getStartTime() == null;
        LocalDateTime start = allDay ? todo.getTodoDate().atStartOfDay()
                : todo.getTodoDate().atTime(todo.getStartTime());
        LocalDateTime end = allDay ? start.plusDays(1) : todo.getTodoDate().atTime(todo.getEndTime());
        CalendarType type = todo.getCategory() == TodoCategory.AI_GOAL_TASK
                ? CalendarType.AI_GOAL : CalendarType.valueOf(todo.getCategory().name());
        return new CalendarEvent(type, todo.getId(), todo.getContent(), start, end,
                allDay, null, todo.getStatusNote(), todo.getCalendarUid());
    }
}
