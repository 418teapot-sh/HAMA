package com.hama.domain.schedule.dto;

import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "캘린더에 표시된 날짜와 해당 반복 발생의 원래 시작 일시(KST)")
public record ScheduleCompletionRequest(
        @NotNull @JsonDeserialize(using = StrictDeserializers.Date.class)
        @Schema(example = "2026-10-03") LocalDate date,
        @NotNull @JsonDeserialize(using = StrictDeserializers.DateTime.class)
        @Schema(example = "2026-10-02T23:00:00") LocalDateTime occurrenceStartAt) {
}
