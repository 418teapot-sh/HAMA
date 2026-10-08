package com.hama.domain.goalai.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Schema(description = """
        초안 부분 수정. 바꿀 필드만 보냅니다. 생략한 필드는 유지하고 null 을 보내면 지웁니다.
        title·startDate·endDate 는 null 이어도 유지합니다(필수). 현실성 체크 suggestions 의 patch 를 그대로 보내도 됩니다.
        """)
public class UpdateGoalDraftRequest {

    @Getter
    @Pattern(regexp = "(?s).*\\S.*", message = "목표 제목은 공백일 수 없습니다.")
    @Size(max = 100, message = "목표 제목은 100자 이하입니다.")
    @Schema(example = "3개월 안에 토익 850점 달성", maxLength = 100)
    private String title;

    @Getter
    @Size(max = 50, message = "측정 지표명은 50자 이하입니다.")
    private String metricName;

    @Getter
    @Size(max = 20, message = "단위는 20자 이하입니다.")
    private String unit;

    @Getter
    @Digits(integer = 8, fraction = 2, message = "시작 수준은 정수 8자리, 소수 2자리 이하입니다.")
    private BigDecimal startValue;

    @Getter
    @Digits(integer = 8, fraction = 2, message = "목표값은 정수 8자리, 소수 2자리 이하입니다.")
    @Schema(example = "800")
    private BigDecimal targetValue;

    @Getter
    @Schema(example = "2026-10-01")
    private LocalDate startDate;

    @Getter
    @Schema(example = "2027-01-31", description = "오늘(KST) 이후여야 합니다.")
    private LocalDate endDate;

    @Getter
    @DecimalMin(value = "0.0", inclusive = false, message = "주간 가용시간은 0보다 커야 합니다.")
    @DecimalMax(value = "168.0", message = "주간 가용시간은 168시간 이하입니다.")
    @Digits(integer = 3, fraction = 1, message = "주간 가용시간은 소수 첫째 자리까지입니다.")
    @Schema(example = "12.0")
    private BigDecimal weeklyAvailableHours;

    @Getter
    @Size(max = 1000, message = "현재 수준은 1000자 이하입니다.")
    private String currentLevel;

    private boolean metricNamePresent;
    private boolean unitPresent;
    private boolean startValuePresent;
    private boolean targetValuePresent;
    private boolean weeklyAvailableHoursPresent;
    private boolean currentLevelPresent;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
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

    @JsonSetter("startDate")
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    @JsonSetter("weeklyAvailableHours")
    public void setWeeklyAvailableHours(BigDecimal weeklyAvailableHours) {
        this.weeklyAvailableHours = weeklyAvailableHours;
        this.weeklyAvailableHoursPresent = true;
    }

    @JsonSetter("currentLevel")
    public void setCurrentLevel(String currentLevel) {
        this.currentLevel = currentLevel;
        this.currentLevelPresent = true;
    }

    public GoalDraft mergeInto(GoalDraft current) {
        return new GoalDraft(
                title == null ? current.title() : title.strip(),
                metricNamePresent ? metricName : current.metricName(),
                unitPresent ? unit : current.unit(),
                startValuePresent ? startValue : current.startValue(),
                targetValuePresent ? targetValue : current.targetValue(),
                startDate == null ? current.startDate() : startDate,
                endDate == null ? current.endDate() : endDate,
                weeklyAvailableHoursPresent ? weeklyAvailableHours : current.weeklyAvailableHours(),
                currentLevelPresent ? currentLevel : current.currentLevel());
    }
}
