package com.hama.domain.goalai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GoalDraftTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private static GoalDraft draft(String title, LocalDate start, LocalDate end) {
        return new GoalDraft(title, "토익 점수", "점", new BigDecimal("700.456"), new BigDecimal("850"),
                start, end, new BigDecimal("10.04"), "LC 350");
    }

    @Test
    void 저장할_수_있게_자르고_자릿수를_맞춘다() {
        GoalDraft normalized = draft("  " + "가".repeat(120) + "  ", TODAY, TODAY.plusDays(90)).normalized(TODAY);

        assertThat(normalized.title()).hasSize(100);
        assertThat(normalized.startValue()).isEqualByComparingTo("700.46");
        assertThat(normalized.weeklyAvailableHours()).isEqualByComparingTo("10.0");
    }

    @Test
    void 제목이나_기간이_없거나_잘못되면_null() {
        assertThat(draft(" ", TODAY, TODAY.plusDays(1)).normalized(TODAY)).isNull();
        assertThat(draft("토익", null, TODAY.plusDays(1)).normalized(TODAY)).isNull();
        assertThat(draft("토익", TODAY.plusDays(5), TODAY.plusDays(1)).normalized(TODAY)).isNull();
        assertThat(draft("토익", TODAY.minusDays(30), TODAY.minusDays(1)).normalized(TODAY)).isNull();
        assertThat(draft("토익", TODAY, LocalDate.of(10000, 1, 1)).normalized(TODAY)).isNull();
    }

    @Test
    void 범위를_벗어난_주간_가용시간은_비운다() {
        GoalDraft tooMany = new GoalDraft("토익", null, null, null, null, TODAY, TODAY.plusDays(1),
                new BigDecimal("200"), null);

        assertThat(tooMany.normalized(TODAY).weeklyAvailableHours()).isNull();
    }
}
