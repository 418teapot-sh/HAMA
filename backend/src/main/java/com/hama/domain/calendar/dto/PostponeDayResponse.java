package com.hama.domain.calendar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PostponeDayResponse(int movedCount, List<Moved> moved, List<Unplaced> unplaced) {
    public record Moved(Long todoId, LocalDate fromDate,
            @Schema(description = "기존 시간이 없으면 null", nullable = true)
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime from,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime to) {
    }

    public record Unplaced(Long todoId,
            @Schema(description = "TODO_POSTPONE_NO_SLOT 또는 TODO_GOAL_NOT_IN_PROGRESS") String reason) {
    }
}
