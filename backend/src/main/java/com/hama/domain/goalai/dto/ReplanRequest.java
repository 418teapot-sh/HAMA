package com.hama.domain.goalai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record ReplanRequest(
        @NotNull(message = "목표 id 는 필수입니다.") @Positive(message = "목표 id 가 올바르지 않습니다.")
        @Schema(example = "3") Long goalId,
        @Schema(description = "생략하면 오늘(KST)과 목표 시작일 중 늦은 날. 오늘 이후이면서 목표 기간 안이어야 합니다.",
                example = "2026-10-08")
        LocalDate fromDate) {
}
