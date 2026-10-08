package com.hama.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.hama.domain.goal.entity.GoalContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Schema(description = "목표 부분 수정. 생략한 필드는 기존 값을 유지하고, null 을 보내면 지웁니다. title 은 null 이어도 유지합니다.")
public class UpdateGoalRequest {

    @Getter
    @Pattern(regexp = "(?s).*\\S.*", message = "목표 제목은 공백일 수 없습니다.")
    @Size(max = 100, message = "목표 제목은 100자 이하입니다.")
    @Schema(example = "3개월 안에 토익 850점", maxLength = 100, description = "생략하거나 null 이면 유지")
    private String title;

    @Getter
    @Schema(description = "UTF-8 기준 65535바이트 이하. 넘으면 400(GOAL_DESCRIPTION_TOO_LONG). null 이면 지움")
    private String description;

    @Getter
    @Size(max = 50, message = "측정 지표명은 50자 이하입니다.")
    @Schema(description = "생략 시 유지, null 이면 지움")
    private String metricName;

    @Getter
    @Size(max = 20, message = "단위는 20자 이하입니다.")
    @Schema(description = "생략 시 유지, null 이면 지움")
    private String unit;

    @Getter
    @Digits(integer = 8, fraction = 2, message = "시작 수준은 정수 8자리, 소수 2자리 이하입니다.")
    @Schema(description = "생략 시 유지, null 이면 지움")
    private BigDecimal startValue;

    @Getter
    @Digits(integer = 8, fraction = 2, message = "목표값은 정수 8자리, 소수 2자리 이하입니다.")
    @Schema(example = "850", description = "생략 시 유지, null 이면 지움")
    private BigDecimal targetValue;

    @Getter
    @DecimalMin(value = "0.0", inclusive = false, message = "주간 가용시간은 0보다 커야 합니다.")
    @DecimalMax(value = "168.0", message = "주간 가용시간은 168시간 이하입니다.")
    @Digits(integer = 3, fraction = 1, message = "주간 가용시간은 소수 첫째 자리까지입니다.")
    @Schema(example = "12.0", description = "생략 시 유지, null 이면 지움")
    private BigDecimal weeklyAvailableHours;

    @Getter
    @Schema(description = "생략 시 유지, null 이면 지움(진행 중인 목표는 400)")
    private LocalDate startDate;

    @Getter
    @Schema(example = "2026-12-31", description = "오늘(KST) 이후여야 합니다. 생략 시 유지, null 이면 지움(진행 중인 목표는 400)")
    private LocalDate endDate;

    private boolean descriptionPresent;
    private boolean metricNamePresent;
    private boolean unitPresent;
    private boolean startValuePresent;
    private boolean targetValuePresent;
    private boolean weeklyAvailableHoursPresent;
    private boolean startDatePresent;
    private boolean endDatePresent;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonSetter("metricName")
    public void setMetricName(String metricName) {
        this.metricName = metricName;
        this.metricNamePresent = true;
    }

    @JsonSetter("unit")
    public void setUnit(String unit) {
        this.unit = unit;
        this.unitPresent = true;
    }

    @JsonSetter("startValue")
    public void setStartValue(BigDecimal startValue) {
        this.startValue = startValue;
        this.startValuePresent = true;
    }

    @JsonSetter("targetValue")
    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
        this.targetValuePresent = true;
    }

    @JsonSetter("weeklyAvailableHours")
    public void setWeeklyAvailableHours(BigDecimal weeklyAvailableHours) {
        this.weeklyAvailableHours = weeklyAvailableHours;
        this.weeklyAvailableHoursPresent = true;
    }

    @JsonSetter("startDate")
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDatePresent = true;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDatePresent = true;
    }

    public GoalContent mergeInto(GoalContent current) {
        return new GoalContent(
                title == null ? current.title() : title,
                descriptionPresent ? description : current.description(),
                metricNamePresent ? metricName : current.metricName(),
                unitPresent ? unit : current.unit(),
                startValuePresent ? startValue : current.startValue(),
                targetValuePresent ? targetValue : current.targetValue(),
                weeklyAvailableHoursPresent ? weeklyAvailableHours : current.weeklyAvailableHours(),
                startDatePresent ? startDate : current.startDate(),
                endDatePresent ? endDate : current.endDate());
    }
}
