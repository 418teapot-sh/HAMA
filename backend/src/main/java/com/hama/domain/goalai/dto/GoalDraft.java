package com.hama.domain.goalai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalContent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** AI 가 정리하고 사용자가 고칠 수 있는 목표 초안. 확정하면 이 값으로 Goal 을 만듭니다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoalDraft(
        String title,
        String metricName,
        String unit,
        BigDecimal startValue,
        BigDecimal targetValue,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal weeklyAvailableHours,
        String currentLevel
) {

    private static final BigDecimal MAX_VALUE = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_WEEKLY_HOURS = new BigDecimal("168.0");

    /**
     * AI 가 준 초안을 DB 에 담을 수 있는 모양으로 맞춥니다. 제목·기간이 없거나 기간이 잘못됐으면 null(아직 수집 중)입니다.
     * AI 는 길이·자릿수를 지키지 않을 수 있어서 거절하지 않고 자르거나 비웁니다.
     */
    public GoalDraft normalized(LocalDate today) {
        if (title == null || title.isBlank() || startDate == null || endDate == null
                || !storable(startDate) || !storable(endDate)
                || startDate.isAfter(endDate) || endDate.isBefore(today)
                || !Goal.withinMaxPeriod(startDate, endDate)) {
            return null;
        }
        return new GoalDraft(cut(title.strip(), 100), cut(metricName, 50), cut(unit, 20),
                value(startValue), value(targetValue), startDate, endDate,
                weeklyHours(weeklyAvailableHours), cut(currentLevel, 1000));
    }

    /** 날짜 순서는 맞는데 기간만 상한(1년)을 넘는지. 이때는 사용자에게 기간을 다시 물어야 합니다. */
    public boolean exceedsMaxPeriod() {
        return startDate != null && endDate != null && !startDate.isAfter(endDate)
                && !Goal.withinMaxPeriod(startDate, endDate);
    }

    public GoalContent toContent() {
        return new GoalContent(title, null, metricName, unit, startValue, targetValue,
                weeklyAvailableHours, startDate, endDate);
    }

    private static boolean storable(LocalDate date) {
        return date.getYear() >= 1000 && date.getYear() <= 9999;
    }

    private static String cut(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    private static BigDecimal value(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return scaled.abs().compareTo(MAX_VALUE) > 0 ? null : scaled;
    }

    private static BigDecimal weeklyHours(BigDecimal hours) {
        if (hours == null) {
            return null;
        }
        BigDecimal scaled = hours.setScale(1, RoundingMode.HALF_UP);
        return scaled.signum() <= 0 || scaled.compareTo(MAX_WEEKLY_HOURS) > 0 ? null : scaled;
    }
}
