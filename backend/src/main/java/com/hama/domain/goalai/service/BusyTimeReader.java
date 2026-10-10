package com.hama.domain.goalai.service;

import com.hama.domain.calendar.dto.CalendarQuery;
import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.calendar.service.CalendarReadRepository;
import com.hama.domain.calendar.service.ScheduleOccurrences;
import com.hama.domain.goalai.service.TimeSlotPlacer.Interval;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 고정·개인 일정(반복 포함)과 시간이 정해진 투두를 날짜별 바쁜 구간으로 모읍니다. */
@Component
@RequiredArgsConstructor
public class BusyTimeReader {

    /** CalendarQuery 는 366일 미만만 받습니다. */
    private static final int CHUNK_DAYS = 365;
    private static final int PAGE = 100;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final CalendarReadRepository calendar;
    private final ScheduleOccurrences occurrences;

    /** @param excludedTodoIds 다시 배치할 투두처럼 바쁜 시간으로 보지 않을 투두 */
    public Map<LocalDate, List<Interval>> read(long userId, LocalDate from, LocalDate to, Set<Long> excludedTodoIds) {
        Map<LocalDate, List<Interval>> busy = new HashMap<>();
        for (LocalDate chunkFrom = from; !chunkFrom.isAfter(to); chunkFrom = chunkFrom.plusDays(CHUNK_DAYS)) {
            LocalDate chunkTo = chunkFrom.plusDays(CHUNK_DAYS - 1).isAfter(to) ? to : chunkFrom.plusDays(CHUNK_DAYS - 1);
            LocalDate first = chunkFrom;
            LocalDate last = chunkTo;
            CalendarQuery schedules = new CalendarQuery(first, last, Set.of(CalendarType.FIXED, CalendarType.PERSONAL));
            CalendarQuery tasks = new CalendarQuery(first, last, Set.of(CalendarType.TASK, CalendarType.AI_GOAL));

            forEachPage(afterId -> calendar.schedules(userId, schedules, afterId), CalendarReadRepository.ScheduleCandidate::id,
                    schedule -> occurrences.forEach(schedule.source(), first, last,
                            occurrence -> add(busy, occurrence.startAt(), occurrence.endAt(), first, last)));
            forEachPage(afterId -> calendar.tasks(userId, tasks, afterId), CalendarReadRepository.TaskCandidate::id,
                    task -> {
                        if (task.startTime() != null && task.endTime() != null
                                && !excludedTodoIds.contains(task.id())) {
                            busy.computeIfAbsent(task.date(), d -> new ArrayList<>())
                                    .add(new Interval(minute(task.startTime()), minute(task.endTime())));
                        }
                    });
        }
        return busy;
    }

    /** 여러 날에 걸친 일정은 날짜마다 잘라 넣습니다. 종일 일정은 다음 날 0시에 끝나므로 그날 전체가 됩니다. */
    private static void add(Map<LocalDate, List<Interval>> busy, LocalDateTime start, LocalDateTime end,
            LocalDate from, LocalDate to) {
        LocalDate day = start.toLocalDate().isBefore(from) ? from : start.toLocalDate();
        for (; !day.isAfter(to) && day.atStartOfDay().isBefore(end); day = day.plusDays(1)) {
            LocalDateTime dayStart = day.atStartOfDay();
            int s = start.isAfter(dayStart) ? (int) Duration.between(dayStart, start).toMinutes() : 0;
            // 종료 시각의 초를 버리면 다음 투두와 겹칠 수 있어 종료 분은 올림합니다.
            int e = end.isBefore(dayStart.plusDays(1))
                    ? (int) Math.ceilDiv(Duration.between(dayStart, end).toNanos(), Duration.ofMinutes(1).toNanos())
                    : MINUTES_PER_DAY;
            if (s < e) {
                busy.computeIfAbsent(day, d -> new ArrayList<>()).add(new Interval(s, e));
            }
        }
    }

    private static int minute(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static <T> void forEachPage(LongFunction<List<T>> page, ToLongFunction<T> id, Consumer<T> consumer) {
        long afterId = 0;
        while (true) {
            List<T> rows = page.apply(afterId);
            rows.forEach(consumer);
            if (rows.size() < PAGE) {
                return;
            }
            afterId = id.applyAsLong(rows.getLast());
        }
    }
}
