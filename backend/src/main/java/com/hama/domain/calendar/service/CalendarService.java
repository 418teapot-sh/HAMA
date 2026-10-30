package com.hama.domain.calendar.service;

import com.hama.domain.calendar.dto.CalendarQuery;
import com.hama.domain.calendar.dto.CalendarResponse;
import com.hama.domain.calendar.dto.CalendarResponse.Item;
import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.calendar.service.CalendarReadRepository.ScheduleCandidate;
import com.hama.domain.calendar.exception.CalendarErrorCode;
import com.hama.global.exception.BusinessException;
import com.hama.domain.schedule.repository.ScheduleCompletionRepository;
import com.hama.domain.todo.entity.TodoStatus;
import java.util.function.Consumer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CalendarService {

    public static final int RESULT_LIMIT = 10_000;
    private final CalendarReadRepository reader;
    private final ScheduleOccurrences occurrences;
    private final IcsWriter icsWriter;
    private final ScheduleCompletionRepository completions;

    private record CompletionKey(long scheduleId, LocalDate occurrenceDate, LocalDate displayDate) {}

    public CalendarResponse get(Long userId, CalendarQuery query) {
        List<Item> items = new ArrayList<>();
        schedules(userId, query, schedule -> occurrences.forEach(schedule.source(), query.from(), query.to(),
                occurrence -> split(items, schedule, occurrence.startAt(), occurrence.endAt(), query)));
        tasks(userId, query, todo -> {
            requireCapacity(items);
            items.add(new Item(todo.type(), todo.id(), todo.title(), todo.date(),
                    todo.startTime(), todo.endTime(), todo.startTime() == null, todo.goalId(), todo.status(), null));
        });
        List<Long> scheduleIds = items.stream().filter(item -> item.occurrenceStartAt() != null)
                .map(Item::refId).distinct().toList();
        Set<CompletionKey> completed = new HashSet<>();
        for (int offset = 0; offset < scheduleIds.size(); offset += 500) {
            var batch = scheduleIds.subList(offset, Math.min(offset + 500, scheduleIds.size()));
            completions.findForCalendar(userId, batch, query.from(), query.to()).forEach(completion -> completed.add(
                    new CompletionKey(completion.getScheduleId(), completion.getOccurrenceDate(), completion.getDisplayDate())));
        }
        items.replaceAll(item -> item.occurrenceStartAt() == null ? item : new Item(item.type(), item.refId(), item.title(),
                item.date(), item.startTime(), item.endTime(), item.allDay(), item.goalId(),
                completed.contains(new CompletionKey(item.refId(), item.occurrenceStartAt().toLocalDate(), item.date()))
                        ? TodoStatus.COMPLETED : TodoStatus.PENDING, item.occurrenceStartAt()));
        items.sort(Comparator.comparing(Item::date)
                .thenComparing(Item::startTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Item::type).thenComparing(Item::refId));
        return new CalendarResponse(List.copyOf(items));
    }

    public byte[] export(Long userId, CalendarQuery query) {
        List<CalendarEvent> events = new ArrayList<>();
        schedules(userId, query, schedule -> {
            if (occurrences.overlaps(schedule.source(), query.from(), query.to())) {
                requireCapacity(events);
                events.add(schedule.event());
            }
        });
        tasks(userId, query, todo -> {
            requireCapacity(events);
            events.add(todo.event());
        });
        return icsWriter.write(events);
    }

    private void schedules(long userId, CalendarQuery query, Consumer<ScheduleCandidate> consumer) {
        long cursor = 0;
        while (true) {
            List<ScheduleCandidate> batch = reader.schedules(userId, query, cursor);
            if (batch.isEmpty()) return;
            batch.stream().filter(schedule -> occurrences.hasReadableRepeatRule(schedule.id(), schedule.source()))
                    .forEach(consumer);
            cursor = batch.getLast().id();
        }
    }

    private void tasks(long userId, CalendarQuery query, Consumer<CalendarReadRepository.TaskCandidate> consumer) {
        long cursor = 0;
        while (true) {
            List<CalendarReadRepository.TaskCandidate> batch = reader.tasks(userId, query, cursor);
            if (batch.isEmpty()) return;
            batch.forEach(consumer);
            cursor = batch.getLast().id();
        }
    }

    private static void requireCapacity(List<?> results) {
        if (results.size() >= RESULT_LIMIT) {
            throw new BusinessException(CalendarErrorCode.CALENDAR_RESULT_LIMIT_EXCEEDED);
        }
    }

    private static void split(List<Item> items, ScheduleCandidate schedule, LocalDateTime start,
            LocalDateTime end, CalendarQuery query) {
        LocalDate first = start.toLocalDate().isBefore(query.from()) ? query.from() : start.toLocalDate();
        for (LocalDate day = first; !day.isAfter(query.to()) && day.atStartOfDay().isBefore(end); day = day.plusDays(1)) {
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime nextDay = day.plusDays(1).atStartOfDay();
            LocalTime startTime = schedule.source().allDay() ? null : (start.isAfter(dayStart) ? start.toLocalTime() : LocalTime.MIDNIGHT);
            LocalTime endTime = schedule.source().allDay() ? null : (end.isBefore(nextDay) ? end.toLocalTime() : LocalTime.MIDNIGHT);
            requireCapacity(items);
            items.add(new Item(schedule.type(), schedule.id(), schedule.title(),
                    day, startTime, endTime, schedule.source().allDay(), null, TodoStatus.PENDING, start));
        }
    }
}
