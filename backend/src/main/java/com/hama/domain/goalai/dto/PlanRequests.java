package com.hama.domain.goalai.dto;

import com.hama.domain.goalai.entity.PlanPreference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class PlanRequests {

    private PlanRequests() {
    }

    public record Generate(
            @NotNull(message = "목표 id 는 필수입니다.") @Positive(message = "목표 id 가 올바르지 않습니다.")
            @Schema(example = "3") Long goalId,
            @Schema(description = "생략하면 BALANCED", example = "BALANCED") PlanPreference preference) {
    }
}
