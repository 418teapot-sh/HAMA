package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.calendar.service.CalendarReadRepository;
import com.hama.domain.calendar.service.ScheduleOccurrences;
import com.hama.domain.schedule.recurrence.ScheduleRecurrence;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BusyTimeReaderTest {
    private final CalendarReadRepository calendar = mock(CalendarReadRepository.class);
    private final BusyTimeReader reader = new BusyTimeReader(calendar, new ScheduleOccurrences());
    private final LocalDate day = LocalDate.of(2026, 10, 2);

    private TimeSlotPlacer occupied(String start, String end, boolean allDay, String repeat) {
        var source = new ScheduleRecurrence.Source(LocalDateTime.parse(start), LocalDateTime.parse(end), allDay, repeat);
        when(calendar.schedules(eq(1L), any(), eq(0L))).thenReturn(List.of(
                new CalendarReadRepository.ScheduleCandidate(1L, CalendarType.FIXED, "일정", source, null)));
        return new TimeSlotPlacer(reader.read(1L, day, day, Set.of()));
    }

    @ParameterizedTest
    @CsvSource({"10:00:00,10:00", "10:00:01,10:10", "10:00:59,10:10", "10:01:00,10:10"})
    void 종료초가_있으면_자동배치가_실제종료시각과_겹치지않는다(String end, String expected) {
        var placer = occupied("2026-10-02T09:00:00", "2026-10-02T" + end, false, null);
        assertThat(placer.place(day, 30).start()).isEqualTo(LocalTime.parse(expected));
    }

    @Test
    void 같은분_안의_짧은일정도_직접지정과_겹치며_다음분에는_겹치지않는다() {
        var placer = occupied("2026-10-02T10:00:10", "2026-10-02T10:00:20", false, null);
        assertThat(placer.reserve(day, LocalTime.of(10, 0), LocalTime.of(10, 1))).isFalse();
        assertThat(placer.reserve(day, LocalTime.of(10, 1), LocalTime.of(10, 2))).isTrue();
    }

    @Test
    void 자정을_넘긴_일정의_마지막_초도_점유한다() {
        var placer = occupied("2026-10-01T23:59:50", "2026-10-02T00:00:01", false, null);
        assertThat(placer.reserve(day, LocalTime.MIDNIGHT, LocalTime.of(0, 1))).isFalse();
        assertThat(placer.reserve(day, LocalTime.of(0, 1), LocalTime.of(0, 2))).isTrue();
    }

    @Test
    void 종일일정의_자정_종료경계는_다음날을_점유하지않는다() {
        var placer = occupied("2026-10-01T00:00:00", "2026-10-02T00:00:00", true, null);
        assertThat(placer.reserve(day, LocalTime.MIDNIGHT, LocalTime.of(0, 1))).isTrue();
        assertThat(occupied("2026-10-02T00:00:00", "2026-10-03T00:00:00", true, null).place(day, 30)).isNull();
    }

    @Test
    void 반복발생의_종료초에도_동일한_규칙을_적용한다() {
        var placer = occupied("2026-10-01T09:00:00", "2026-10-01T10:00:01", false, "FREQ=DAILY");
        assertThat(placer.place(day, 30).start()).isEqualTo(LocalTime.of(10, 10));
    }
}
