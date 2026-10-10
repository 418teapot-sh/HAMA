package com.hama.domain.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hama.domain.goalai.service.BusyTimeReader;
import com.hama.domain.goalai.service.TimeSlotPlacer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiPostponeSlotsTest {
    private final BusyTimeReader reader = mock(BusyTimeReader.class);
    private final LocalDate day = LocalDate.of(2026,10,1);

    @ParameterizedTest
    @CsvSource({"08:59:59,09:00", "12:00:00,12:00", "12:00:01,12:10", "21:30:00,21:30"})
    void 현재시각의_초를_버리지않고_10분단위로_배치한다(String now, String expected) {
        when(reader.read(1,day,day,Set.of(2L))).thenReturn(Map.of());
        var slots = new AiPostponeSlots(reader,1,Set.of(2L),day.atTime(LocalTime.parse(now)));
        slots.load(day,day);
        assertThat(slots.place(day,30).start()).isEqualTo(LocalTime.parse(expected));
    }

    @Test void 오늘_작업시간이_끝나면_오늘은_없고_다음날은_가능하다() {
        when(reader.read(1,day,day.plusDays(1),Set.of())).thenReturn(Map.of());
        var slots = new AiPostponeSlots(reader,1,Set.of(),day.atTime(22,0));
        slots.load(day,day.plusDays(1));
        assertThat(slots.place(day,30)).isNull();
        assertThat(slots.place(day.plusDays(1),30).start()).isEqualTo(LocalTime.of(9,0));
    }

    @Test void 겹친탐색범위는_새날짜만_읽고_기존예약을_보존한다() {
        when(reader.read(1,day,day.plusDays(1),Set.of())).thenReturn(Map.of());
        when(reader.read(1,day.plusDays(2),day.plusDays(2),Set.of())).thenReturn(
                Map.of(day.plusDays(2),List.of(new TimeSlotPlacer.Interval(540,600))));
        var slots = new AiPostponeSlots(reader,1,Set.of(),day.minusDays(1).atStartOfDay());
        slots.load(day,day.plusDays(1));
        assertThat(slots.place(day.plusDays(1),60).start()).isEqualTo(LocalTime.of(9,0));
        slots.load(day.plusDays(1),day.plusDays(2));
        assertThat(slots.place(day.plusDays(1),60).start()).isEqualTo(LocalTime.of(10,0));
        assertThat(slots.place(day.plusDays(2),30).start()).isEqualTo(LocalTime.of(10,0));
        verify(reader,times(1)).read(1,day,day.plusDays(1),Set.of());
        verify(reader,times(1)).read(1,day.plusDays(2),day.plusDays(2),Set.of());
        verifyNoMoreInteractions(reader);
    }
}
