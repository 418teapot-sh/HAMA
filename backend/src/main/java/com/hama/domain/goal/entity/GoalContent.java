package com.hama.domain.goal.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 사용자가 입력·수정할 수 있는 목표 정보. 생성과 수정이 같은 검증을 타도록 한 묶음으로 넘깁니다.
 */
public record GoalContent(
        String title,
        String description,
        String metricName,
        String unit,
        BigDecimal startValue,
        BigDecimal targetValue,
        BigDecimal weeklyAvailableHours,
        LocalDate startDate,
        LocalDate endDate
) {
}
