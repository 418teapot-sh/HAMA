package com.hama.domain.todo.service;

import com.hama.domain.goalai.service.BusyTimeReader;
import com.hama.domain.goalai.service.TimeSlotPlacer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 요청 안에서 이미 읽은 날짜와 예약한 슬롯을 공유합니다. 배치의 목표별 탐색 기간은 서로 다를 수 있습니다. */
final class AiPostponeSlots {
    private final BusyTimeReader reader;
    private final long userId;
    private final Set<Long> excluded;
    private final LocalDateTime now;
    private final Map<LocalDate, TimeSlotPlacer> days = new HashMap<>();

    AiPostponeSlots(BusyTimeReader reader, long userId, Set<Long> excluded, LocalDateTime now) {
        this.reader = reader;
        this.userId = userId;
        this.excluded = excluded;
        this.now = now;
    }

    void load(LocalDate from, LocalDate to) {
        LocalDate date = from;
        while (!date.isAfter(to)) {
            if (days.containsKey(date)) {
                date = date.plusDays(1);
                continue;
            }
            LocalDate first = date;
            while (date.isBefore(to) && !days.containsKey(date.plusDays(1))) date = date.plusDays(1);
            LocalDate last = date;
            var busy = reader.read(userId, first, last, excluded);
            for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                var intervals = new ArrayList<>(busy.getOrDefault(day, List.of()));
                if (day.equals(now.toLocalDate())) {
                    // 분 미만도 버리지 않습니다. 실제 12:00:01이면 12:10부터 배치합니다.
                    int end = (int) ((now.toLocalTime().toNanoOfDay() + 59_999_999_999L) / 60_000_000_000L);
                    if (end > 0) intervals.add(new TimeSlotPlacer.Interval(0, end));
                }
                days.put(day, new TimeSlotPlacer(Map.of(day, intervals)));
            }
            date = last.plusDays(1);
        }
    }

    TimeSlotPlacer.Slot place(LocalDate day, int minutes) {
        return days.get(day).place(day, minutes);
    }
}
