package com.hama.domain.goal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.goal.entity.GoalContent;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UpdateGoalRequestTest {

    private static final GoalContent CURRENT = new GoalContent("매일 러닝 30분", "체력 기르기", "체중", "kg",
            new BigDecimal("80.0"), new BigDecimal("74.0"), new BigDecimal("5.0"),
            LocalDate.of(2026, 10, 4), LocalDate.of(2026, 12, 31));

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 생략한_필드는_유지하고_null_은_지운다() {
        UpdateGoalRequest request = mapper.readValue("""
                {"metricName":null,"unit":null,"targetValue":850}
                """, UpdateGoalRequest.class);

        GoalContent merged = request.mergeInto(CURRENT);

        assertThat(merged.metricName()).isNull();
        assertThat(merged.unit()).isNull();
        assertThat(merged.targetValue()).isEqualByComparingTo("850");
        assertThat(merged.description()).isEqualTo("체력 기르기");
        assertThat(merged.startValue()).isEqualByComparingTo("80.0");
        assertThat(merged.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void title_은_null_이어도_유지한다() {
        UpdateGoalRequest request = mapper.readValue("{\"title\":null}", UpdateGoalRequest.class);

        assertThat(request.mergeInto(CURRENT)).isEqualTo(CURRENT);
    }
}
