package com.hama.domain.goal.dto;

import com.hama.domain.goal.entity.GoalContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "목표 부분 수정. 변경할 필드만 보내고, 생략하거나 null 인 필드는 기존 값을 유지합니다.")
public record UpdateGoalRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "목표 제목은 공백일 수 없습니다.")
        @Size(max = 100, message = "목표 제목은 100자 이하입니다.")
        @Schema(example = "3개월 안에 토익 850점", maxLength = 100)
        String title,

        @Schema(description = "UTF-8 기준 65535바이트 이하. 넘으면 400(GOAL_DESCRIPTION_TOO_LONG).")
        String description,

        @Size(max = 50, message = "측정 지표명은 50자 이하입니다.")
        String metricName,

        @Size(max = 20, message = "단위는 20자 이하입니다.")
        String unit,

        @Digits(integer = 8, fraction = 2, message = "시작 수준은 정수 8자리, 소수 2자리 이하입니다.")
        BigDecimal startValue,

        @Digits(integer = 8, fraction = 2, message = "목표값은 정수 8자리, 소수 2자리 이하입니다.")
        @Schema(example = "850")
        BigDecimal targetValue,

        @DecimalMin(value = "0.0", inclusive = false, message = "주간 가용시간은 0보다 커야 합니다.")
        @DecimalMax(value = "168.0", message = "주간 가용시간은 168시간 이하입니다.")
        @Digits(integer = 3, fraction = 1, message = "주간 가용시간은 소수 첫째 자리까지입니다.")
        @Schema(example = "12.0")
        BigDecimal weeklyAvailableHours,

        LocalDate startDate,

        @Schema(example = "2026-12-31", description = "오늘(KST) 이후여야 합니다.")
        LocalDate endDate
) {

    public GoalContent mergeInto(GoalContent current) {
        return new GoalContent(
                or(title, current.title()),
                or(description, current.description()),
                or(metricName, current.metricName()),
                or(unit, current.unit()),
                or(startValue, current.startValue()),
                or(targetValue, current.targetValue()),
                or(weeklyAvailableHours, current.weeklyAvailableHours()),
                or(startDate, current.startDate()),
                or(endDate, current.endDate()));
    }

    private static <T> T or(T value, T current) {
        return value == null ? current : value;
    }
}
