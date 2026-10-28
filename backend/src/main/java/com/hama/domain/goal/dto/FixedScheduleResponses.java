package com.hama.domain.goal.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public final class FixedScheduleResponses {

    private FixedScheduleResponses() {
    }

    public record Days(List<Day> days) {
    }

    public record Day(LocalDate date, List<Item> schedules) {
    }

    public record Item(Long goalId, String goalTitle, DayOfWeek dayOfWeek, String content) {
    }
}
