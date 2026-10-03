package com.hama.domain.calendar.service;

import com.hama.domain.calendar.dto.CalendarQuery;
import com.hama.domain.calendar.dto.CalendarResponse;
import com.hama.domain.calendar.dto.CalendarResponse.Item;
import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.repository.ScheduleRepository;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import com.hama.domain.todo.repository.TodoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CalendarService {

    private final ScheduleRepository schedules;
    private final TodoRepository todos;
    private final ScheduleOccurrences occurrences;
    private final IcsWriter icsWriter;

    public CalendarResponse get(Long userId, CalendarQuery query) {
        List<Item> items = new ArrayList<>();
        for (Schedule schedule : schedules(userId, query)) {
            occurrences.forEach(schedule, query.from(), query.to(),
                    occurrence -> split(items, schedule, occurrence.startAt(), occurrence.endAt(), query));
        }
        for (Todo todo : tasks(userId, query)) {
            items.add(new Item(CalendarType.TASK, todo.getId(), todo.getContent(), todo.getTodoDate(),
                    todo.getStartTime(), todo.getEndTime(), todo.getStartTime() == null, null, todo.getStatus()));
        }
        items.sort(Comparator.comparing(Item::date)
                .thenComparing(Item::startTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Item::type).thenComparing(Item::refId));
        return new CalendarResponse(List.copyOf(items));
    }

    public byte[] export(Long userId, CalendarQuery query) {
        List<CalendarEvent> events = new ArrayList<>();
        for (Schedule schedule : schedules(userId, query)) {
            if (occurrences.overlaps(schedule, query.from(), query.to())) {
                events.add(CalendarEvent.from(schedule));
            }
        }
        tasks(userId, query).stream().map(CalendarEvent::from).forEach(events::add);
        return icsWriter.write(events);
    }

    private List<Schedule> schedules(Long userId, CalendarQuery query) {
        if (!query.types().contains(CalendarType.FIXED) && !query.types().contains(CalendarType.PERSONAL)) {
            return List.of();
        }
        return schedules.findCalendarCandidates(userId, query.from().atStartOfDay(),
                        query.to().atTime(23, 59, 59)).stream()
                .filter(s -> query.types().contains(CalendarType.valueOf(s.getType().name()))).toList();
    }

    private List<Todo> tasks(Long userId, CalendarQuery query) {
        // AI 목표 참조·생성 계약은 BE2 연동 후 추가합니다. 일반 TASK만 현재 저장 가능합니다.
        return query.types().contains(CalendarType.TASK)
                ? todos.findActiveBetween(userId, query.from(), query.to(), TodoCategory.TASK) : List.of();
    }

    private static void split(List<Item> items, Schedule schedule, LocalDateTime start,
            LocalDateTime end, CalendarQuery query) {
        LocalDate first = start.toLocalDate().isBefore(query.from()) ? query.from() : start.toLocalDate();
        for (LocalDate day = first; !day.isAfter(query.to()) && day.atStartOfDay().isBefore(end); day = day.plusDays(1)) {
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime nextDay = day.plusDays(1).atStartOfDay();
            LocalTime startTime = schedule.isAllDay() ? null : (start.isAfter(dayStart) ? start.toLocalTime() : LocalTime.MIDNIGHT);
            LocalTime endTime = schedule.isAllDay() ? null : (end.isBefore(nextDay) ? end.toLocalTime() : LocalTime.MIDNIGHT);
            items.add(new Item(CalendarType.valueOf(schedule.getType().name()), schedule.getId(), schedule.getTitle(),
                    day, startTime, endTime, schedule.isAllDay(), null, null));
        }
    }
}
