package com.hama.domain.goal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.goal.entity.GoalContent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UpdateGoalRequestTest {

    private static final GoalContent CURRENT = new GoalContent("매일 러닝 30분", "체중 80kg",
            LocalDate.of(2026, 10, 4), LocalDate.of(2026, 12, 31));

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 생략한_필드는_유지하고_null_은_지운다() {
        UpdateGoalRequest request = mapper.readValue("""
                {"endDate":null}
                """, UpdateGoalRequest.class);

        GoalContent merged = request.mergeInto(CURRENT);

        assertThat(merged.endDate()).isNull();
        assertThat(merged.title()).isEqualTo("매일 러닝 30분");
        assertThat(merged.startDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(merged.currentStatus()).isEqualTo("체중 80kg");
    }

    @Test
    void title_은_null_이어도_유지하고_currentStatus는_항상_그대로다() {
        UpdateGoalRequest request = mapper.readValue("{\"title\":null}", UpdateGoalRequest.class);

        assertThat(request.mergeInto(CURRENT)).isEqualTo(CURRENT);
    }

    @Test
    void 보낸_필드는_값으로_바꾼다() {
        UpdateGoalRequest request = mapper.readValue("""
                {"title":"새 제목","startDate":"2026-11-01","endDate":"2026-11-30"}
                """, UpdateGoalRequest.class);

        GoalContent merged = request.mergeInto(CURRENT);

        assertThat(merged.title()).isEqualTo("새 제목");
        assertThat(merged.startDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(merged.endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(merged.currentStatus()).isEqualTo("체중 80kg");
    }

    @Test
    void 제목은_글자가_2자_이상이어야_하고_null_은_검증하지_않는다() {
        assertThat(titleErrors("{\"title\":\"4\"}")).isNotEmpty();
        assertThat(titleErrors("{\"title\":\"기\"}")).isNotEmpty();
        assertThat(titleErrors("{\"title\":\"   \"}")).isNotEmpty();
        assertThat(titleErrors("{\"title\":\"토익 850\"}")).isEmpty();
        assertThat(titleErrors("{\"title\":null}")).isEmpty();
    }

    private Set<ConstraintViolation<UpdateGoalRequest>> titleErrors(String body) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validateProperty(mapper.readValue(body, UpdateGoalRequest.class), "title");
        }
    }
}
