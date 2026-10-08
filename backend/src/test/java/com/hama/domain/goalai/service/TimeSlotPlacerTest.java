package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.goalai.service.TimeSlotPlacer.Interval;
import com.hama.domain.goalai.service.TimeSlotPlacer.Slot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeSlotPlacerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    private static Slot slot(String start, String end) {
        return new Slot(LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void 빈_날은_09시부터_차례로_채운다() {
        TimeSlotPlacer placer = new TimeSlotPlacer(Map.of());

        assertThat(placer.place(DAY, 60)).isEqualTo(slot("09:00", "10:00"));
        assertThat(placer.place(DAY, 45)).isEqualTo(slot("10:00", "10:45"));
        assertThat(placer.place(DAY, 30)).isEqualTo(slot("10:50", "11:20"));
    }

    @Test
    void 일정과_겹치지_않는_첫_빈_시간에_둔다() {
        TimeSlotPlacer placer = new TimeSlotPlacer(Map.of(DAY, List.of(
                new Interval(9 * 60, 18 * 60), new Interval(18 * 60 + 15, 19 * 60))));

        assertThat(placer.place(DAY, 60)).isEqualTo(slot("19:00", "20:00"));
        assertThat(placer.place(DAY, 10)).isEqualTo(slot("18:00", "18:10"));
    }

    @Test
    void 일정_사이_틈이_모자라면_뒤로_넘긴다() {
        TimeSlotPlacer placer = new TimeSlotPlacer(Map.of(DAY, List.of(
                new Interval(10 * 60, 11 * 60), new Interval(9 * 60, 9 * 60 + 40))));

        assertThat(placer.place(DAY, 30)).isEqualTo(slot("11:00", "11:30"));
        assertThat(placer.place(DAY, 20)).isEqualTo(slot("09:40", "10:00"));
    }

    @Test
    void 종일_일정이나_가득_찬_날에는_두지_않는다() {
        TimeSlotPlacer placer = new TimeSlotPlacer(Map.of(
                DAY, List.of(new Interval(0, 24 * 60)),
                DAY.plusDays(1), List.of(new Interval(9 * 60, 21 * 60 + 30))));

        assertThat(placer.place(DAY, 10)).isNull();
        assertThat(placer.place(DAY.plusDays(1), 60)).isNull();
        assertThat(placer.place(DAY.plusDays(1), 30)).isEqualTo(slot("21:30", "22:00"));
        assertThat(placer.place(DAY.plusDays(1), 10)).isNull();
    }

    @Test
    void 밤_22시를_넘기지_않는다() {
        TimeSlotPlacer placer = new TimeSlotPlacer(Map.of(DAY, List.of(new Interval(9 * 60, 20 * 60))));

        assertThat(placer.place(DAY, 120)).isEqualTo(slot("20:00", "22:00"));
        assertThat(placer.place(DAY.plusDays(2), 13 * 60 + 1)).isNull();
    }
}
