package com.hama.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CurrentStatusResponse(
        Long goalId,
        String currentStatus,
        @Schema(example = "2026-10-04T13:20:00", description = "마지막 수정 시각(초 단위까지)")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt) {
}
