package com.hama.domain.goal.dto;

import com.hama.domain.goal.entity.GoalContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "목표 직접 생성 (AI 없이). AI 경로는 goals/ai/sessions/{id}/confirm 을 씁니다.")
public record CreateGoalRequest(
        @NotBlank(message = "목표 제목은 필수입니다.")
        @Size(max = 100, message = "목표 제목은 100자 이하입니다.")
        @Schema(example = "매일 러닝 30분", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(example = "체력 기르기", description = "UTF-8 기준 65535바이트 이하. 넘으면 400(GOAL_DESCRIPTION_TOO_LONG).")
        String description,

        @Size(max = 50, message = "측정 지표명은 50자 이하입니다.")
        @Schema(example = "체중", maxLength = 50)
        String metricName,

        @Size(max = 20, message = "단위는 20자 이하입니다.")
        @Schema(example = "kg", maxLength = 20)
        String unit,

        @Digits(integer = 8, fraction = 2, message = "시작 수준은 정수 8자리, 소수 2자리 이하입니다.")
        @Schema(example = "80.0")
        BigDecimal startValue,

        @Digits(integer = 8, fraction = 2, message = "목표값은 정수 8자리, 소수 2자리 이하입니다.")
        @Schema(example = "74.0")
        BigDecimal targetValue,

        @DecimalMin(value = "0.0", inclusive = false, message = "주간 가용시간은 0보다 커야 합니다.")
        @DecimalMax(value = "168.0", message = "주간 가용시간은 168시간 이하입니다.")
        @Digits(integer = 3, fraction = 1, message = "주간 가용시간은 소수 첫째 자리까지입니다.")
        @Schema(example = "5.0")
        BigDecimal weeklyAvailableHours,

        @NotNull(message = "시작일은 필수입니다.")
        @Schema(example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,

        @NotNull(message = "종료일은 필수입니다.")
        @Schema(example = "2026-12-31", description = "오늘(KST) 이후여야 합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate endDate
) {

    public GoalContent toContent() {
        return new GoalContent(title, description, metricName, unit, startValue, targetValue,
                weeklyAvailableHours, startDate, endDate);
    }
}
