package com.hama.domain.calendar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.todo.entity.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(name = "CalendarResponse")
public record CalendarResponse(List<Item> items) {

    @Schema(name = "CalendarItem", description = "날짜별 항목. 종일은 시간 null, 날짜 끝 경계의 endTime 00:00은 다음 날 자정입니다.")
    public record Item(CalendarType type, Long refId, String title, LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            boolean allDay, Long goalId, TodoStatus status) {
    }
}
