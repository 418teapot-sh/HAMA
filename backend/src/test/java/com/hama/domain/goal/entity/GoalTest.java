package com.hama.domain.goal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalTodoCount;
import com.hama.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    private static GoalContent content(LocalDate start, LocalDate end) {
        return new GoalContent("매일 러닝 30분", "체중 80kg", start, end);
    }

    @Test
    void 목표_기간은_시작일_포함_최대_365일이다() {
        LocalDate lastAllowed = TODAY.plusDays(364);
        Goal goal = Goal.create(1L, content(TODAY, lastAllowed), TODAY);

        assertThat(goal.getEndDate()).isEqualTo(lastAllowed);
        assertThatThrownBy(() -> Goal.create(1L, content(TODAY, TODAY.plusDays(365)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_PERIOD_TOO_LONG);
        assertThatThrownBy(() -> goal.revise(content(TODAY, LocalDate.of(9999, 12, 31)), TODAY))
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
    void 직접_생성한_목표는_PLANNING으로_시작한다() {
        Goal goal = Goal.create(1L, content(TODAY, TODAY.plusDays(30)), TODAY);

        assertThat(goal.effectiveStatus(TODAY)).isEqualTo(GoalStatus.PLANNING);
        assertThat(goal.isPast(TODAY)).isFalse();
    }

    @Test
    void 플랜을_적용하면_진행_중이_되고_종료일이_오늘이면_다음날부터_PAST() {
        Goal goal = Goal.create(1L, content(TODAY.minusDays(10), TODAY), TODAY);
        goal.startPlan();

        assertThat(goal.effectiveStatus(TODAY)).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(goal.effectiveStatus(TODAY.plusDays(1))).isEqualTo(GoalStatus.PAST);
    }

    @Test
    void PLANNING이_아니면_플랜을_다시_적용할_수_없다() {
        Goal goal = Goal.create(1L, content(TODAY, TODAY.plusDays(30)), TODAY);
        goal.startPlan();

        assertThatThrownBy(goal::startPlan).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 기간이_뒤집혔거나_종료일이_과거면_생성할_수_없다() {
        assertThatThrownBy(() -> Goal.create(1L, content(TODAY.plusDays(5), TODAY.plusDays(1)), TODAY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
        assertThatThrownBy(() -> Goal.create(1L, content(TODAY.minusDays(5), TODAY.minusDays(1)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
    }

    @Test
    void 직접_생성에는_사용자와_시작일_종료일이_필요하다() {
        assertThatThrownBy(() -> Goal.create(1L, content(null, TODAY), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_INVALID_INPUT);
        assertThatThrownBy(() -> Goal.create(null, content(TODAY, TODAY.plusDays(30)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_INVALID_INPUT);
    }

    @Test
    void 연도가_1000에서_9999_밖인_날짜는_생성도_수정도_할_수_없다() {
        Goal goal = Goal.create(1L, content(TODAY, TODAY.plusDays(30)), TODAY);

        for (GoalContent invalid : List.of(
                content(LocalDate.of(999, 1, 1), TODAY.plusDays(30)),
                content(TODAY, LocalDate.of(10000, 1, 1)))) {
            assertThatThrownBy(() -> Goal.create(1L, invalid, TODAY))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
            assertThatThrownBy(() -> goal.revise(invalid, TODAY))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GoalErrorCode.GOAL_INVALID_PERIOD);
        }
    }

    @Test
    void 지난_목표는_수정할_수_없다() {
        Goal goal = Goal.create(1L, content(TODAY.minusDays(10), TODAY), TODAY);
        goal.startPlan();
        LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> goal.revise(content(TODAY.minusDays(10), TODAY.plusDays(30)), tomorrow))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_EDITABLE);
        assertThatThrownBy(() -> goal.updateCurrentStatus("새 상태", tomorrow))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_EDITABLE);
    }

    @Test
    void 진행_중인_목표를_완료하면_종료일이_지나도_COMPLETED이고_수정할_수_없지만_삭제할_수_있다() {
        Goal goal = Goal.create(1L, content(TODAY, TODAY.plusDays(30)), TODAY);
        goal.startPlan();
        assertThat(goal.isDeletable(TODAY)).isFalse();

        goal.complete(TODAY);

        assertThat(goal.effectiveStatus(TODAY)).isEqualTo(GoalStatus.COMPLETED);
        assertThat(goal.effectiveStatus(TODAY.plusDays(60))).isEqualTo(GoalStatus.COMPLETED);
        assertThat(goal.isPast(TODAY.plusDays(60))).isFalse();
        assertThat(goal.isDeletable(TODAY)).isTrue();
        assertThatThrownBy(() -> goal.revise(content(TODAY, TODAY.plusDays(40)), TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_EDITABLE);
        assertThatThrownBy(() -> goal.updateCurrentStatus("새 상태", TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_EDITABLE);
        assertThatThrownBy(() -> goal.complete(TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_IN_PROGRESS);
    }

    @Test
    void PLANNING이거나_지난_목표는_완료할_수_없다() {
        Goal planning = Goal.create(1L, content(TODAY, TODAY.plusDays(30)), TODAY);
        Goal past = Goal.create(1L, content(TODAY.minusDays(10), TODAY), TODAY);
        past.startPlan();

        assertThatThrownBy(() -> planning.complete(TODAY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_IN_PROGRESS);
        assertThatThrownBy(() -> past.complete(TODAY.plusDays(1)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(GoalErrorCode.GOAL_NOT_IN_PROGRESS);
        assertThat(past.effectiveStatus(TODAY.plusDays(1))).isEqualTo(GoalStatus.PAST);
    }

    @Test
    void 수정은_전체_값을_바꾸고_제목_앞뒤_공백을_지운다() {
        Goal goal = Goal.create(1L, content(TODAY, TODAY.plusDays(30)), TODAY);

        goal.revise(new GoalContent("  토익 850  ", null, TODAY, TODAY.plusDays(60)), TODAY);

        assertThat(goal.getTitle()).isEqualTo("토익 850");
        assertThat(goal.getEndDate()).isEqualTo(TODAY.plusDays(60));
        assertThat(goal.getCurrentStatus()).isNull();
    }

    @Test
    void 현재_상태만_바꾸고_앞뒤_공백을_지운다() {
        Goal goal = Goal.create(1L, content(TODAY, TODAY.plusDays(30)), TODAY);

        goal.updateCurrentStatus("  토익 700점  ", TODAY);
        assertThat(goal.getCurrentStatus()).isEqualTo("토익 700점");
        assertThat(goal.getTitle()).isEqualTo("매일 러닝 30분");

        goal.updateCurrentStatus(null, TODAY);
        assertThat(goal.getCurrentStatus()).isNull();
    }

    @Test
    void 진행률은_소수_첫째_자리까지_반올림하고_투두가_없으면_0() {
        assertThat(new GoalTodoCount(3, 1).progressRate()).isEqualByComparingTo("33.3");
        assertThat(new GoalTodoCount(3, 2).progressRate()).isEqualByComparingTo("66.7");
        assertThat(GoalTodoCount.EMPTY.progressRate()).isEqualByComparingTo("0.0");
        assertThat(new GoalTodoCount(4, 4).progressRate()).isEqualByComparingTo("100.0");
    }
}
