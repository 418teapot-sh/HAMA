package com.hama.domain.goal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalTodoCount;
import com.hama.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    private static GoalContent content(LocalDate start, LocalDate end) {
        return new GoalContent("매일 러닝 30분", null, "체중", "kg",
                new BigDecimal("80.0"), new BigDecimal("74.0"), new BigDecimal("5.0"), start, end);
    }

    private static GoalContent withDescription(String description) {
        return new GoalContent("매일 러닝 30분", description, null, null, null, null, null,
                TODAY, TODAY.plusDays(30));
    }

    @Test
    void 목표_기간은_시작일_포함_최대_365일이다() {
        LocalDate lastAllowed = TODAY.plusDays(364);
        Goal goal = Goal.createDirect(1L, content(TODAY, lastAllowed), TODAY);

        assertThat(goal.getEndDate()).isEqualTo(lastAllowed);
        assertThatThrownBy(() -> Goal.createDirect(1L, content(TODAY, TODAY.plusDays(365)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_PERIOD_TOO_LONG);
        assertThatThrownBy(() -> goal.revise(content(TODAY, LocalDate.of(9999, 12, 31)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_PERIOD_TOO_LONG);
        assertThatThrownBy(() -> Goal.createPlanning(1L, content(TODAY, TODAY.plusYears(2)), null, null, null, TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_PERIOD_TOO_LONG);
    }

    @Test
    void 윤일이_끼어도_상한은_365일이다() {
        assertThat(Goal.withinMaxPeriod(LocalDate.of(2027, 3, 1), LocalDate.of(2028, 2, 28))).isTrue();
        assertThat(Goal.withinMaxPeriod(LocalDate.of(2027, 3, 1), LocalDate.of(2028, 2, 29))).isFalse();
        assertThat(Goal.withinMaxPeriod(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 27))).isTrue();
        assertThat(Goal.withinMaxPeriod(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28))).isFalse();
    }

    @Test
    void 직접_생성한_목표는_진행_중이다() {
        Goal goal = Goal.createDirect(1L, content(TODAY, TODAY.plusDays(30)), TODAY);

        assertThat(goal.effectiveStatus(TODAY)).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(goal.isPast(TODAY)).isFalse();
    }

    @Test
    void 종료일이_오늘이면_아직_진행_중이고_다음날부터_PAST() {
        Goal goal = Goal.createDirect(1L, content(TODAY.minusDays(10), TODAY), TODAY);

        assertThat(goal.effectiveStatus(TODAY)).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(goal.effectiveStatus(TODAY.plusDays(1))).isEqualTo(GoalStatus.PAST);
    }

    @Test
    void 기간이_뒤집혔거나_종료일이_과거면_생성할_수_없다() {
        assertThatThrownBy(() -> Goal.createDirect(1L, content(TODAY.plusDays(5), TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
        assertThatThrownBy(() -> Goal.createDirect(1L, content(TODAY.minusDays(5), TODAY.minusDays(1)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
    }

    @Test
    void 직접_생성에는_시작일과_종료일이_필요하다() {
        assertThatThrownBy(() -> Goal.createDirect(1L, content(null, TODAY), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_INVALID_INPUT);
    }

    @Test
    void 설명은_UTF8_65535바이트까지만_받는다() {
        Goal goal = Goal.createDirect(1L, withDescription("a".repeat(65535)), TODAY);

        assertThat(goal.getDescription()).hasSize(65535);
        // 한글은 3바이트라 21846자면 65538바이트입니다.
        assertThatThrownBy(() -> Goal.createDirect(1L, withDescription("가".repeat(21846)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_DESCRIPTION_TOO_LONG);
        assertThatThrownBy(() -> goal.revise(withDescription("a".repeat(65536)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_DESCRIPTION_TOO_LONG);
    }

    @Test
    void 연도가_1000에서_9999_밖인_날짜는_생성도_수정도_할_수_없다() {
        Goal goal = Goal.createDirect(1L, content(TODAY, TODAY.plusDays(30)), TODAY);

        for (GoalContent invalid : List.of(
                content(LocalDate.of(999, 1, 1), TODAY.plusDays(30)),
                content(TODAY, LocalDate.of(10000, 1, 1)))) {
            assertThatThrownBy(() -> Goal.createDirect(1L, invalid, TODAY))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
            assertThatThrownBy(() -> goal.revise(invalid, TODAY))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
        }
    }

    @Test
    void 지난_목표는_수정할_수_없다() {
        Goal goal = Goal.createDirect(1L, content(TODAY.minusDays(10), TODAY), TODAY);
        LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> goal.revise(content(TODAY.minusDays(10), TODAY.plusDays(30)), tomorrow))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_EDITABLE);
    }

    @Test
    void 수정은_전체_값을_바꾸고_제목_앞뒤_공백을_지운다() {
        Goal goal = Goal.createDirect(1L, content(TODAY, TODAY.plusDays(30)), TODAY);

        goal.revise(new GoalContent("  토익 850  ", "설명", null, null, null, null, null,
                TODAY, TODAY.plusDays(60)), TODAY);

        assertThat(goal.getTitle()).isEqualTo("토익 850");
        assertThat(goal.getEndDate()).isEqualTo(TODAY.plusDays(60));
        assertThat(goal.getMetricName()).isNull();
    }

    @Test
    void 진행률은_소수_첫째_자리까지_반올림하고_투두가_없으면_0() {
        assertThat(new GoalTodoCount(3, 1).progressRate()).isEqualByComparingTo("33.3");
        assertThat(new GoalTodoCount(3, 2).progressRate()).isEqualByComparingTo("66.7");
        assertThat(GoalTodoCount.EMPTY.progressRate()).isEqualByComparingTo("0.0");
        assertThat(new GoalTodoCount(4, 4).progressRate()).isEqualByComparingTo("100.0");
    }
}
