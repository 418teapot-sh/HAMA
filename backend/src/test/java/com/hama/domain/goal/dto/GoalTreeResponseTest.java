package com.hama.domain.goal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.goal.entity.Milestone;
import com.hama.domain.goal.entity.PeriodGoal;
import com.hama.domain.goal.entity.PeriodType;
import com.hama.domain.goal.repository.GoalTodoCount;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GoalTreeResponseTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    private static Milestone milestone(long id, int seq) {
        Milestone milestone = Milestone.create(3L, seq, "단계 " + seq, null, START, START.plusDays(30));
        ReflectionTestUtils.setField(milestone, "id", id);
        return milestone;
    }

    private static PeriodGoal periodGoal(long id, Milestone milestone, int seq) {
        PeriodGoal periodGoal = PeriodGoal.create(milestone, PeriodType.WEEKLY, seq, "주 " + seq,
                START, START.plusDays(6));
        ReflectionTestUtils.setField(periodGoal, "id", id);
        return periodGoal;
    }

    @Test
    void 기간_목표를_마일스톤_아래에_묶고_마일스톤_진행률은_합계로_계산한다() {
        Milestone first = milestone(1L, 1);
        Milestone second = milestone(2L, 2);
        PeriodGoal week1 = periodGoal(10L, first, 1);
        PeriodGoal week2 = periodGoal(11L, first, 2);

        GoalTreeResponse tree = GoalTreeResponse.of(3L, List.of(first, second), List.of(week1, week2),
                Map.of(10L, new GoalTodoCount(4, 3), 11L, new GoalTodoCount(6, 1)));

        assertThat(tree.goalId()).isEqualTo(3L);
        assertThat(tree.milestones()).hasSize(2);

        GoalTreeResponse.MilestoneNode node = tree.milestones().getFirst();
        assertThat(node.periodGoals()).extracting(GoalTreeResponse.PeriodGoalNode::periodGoalId)
                .containsExactly(10L, 11L);
        assertThat(node.periodGoals().getFirst().todoTotal()).isEqualTo(4);
        assertThat(node.periodGoals().getFirst().todoCompleted()).isEqualTo(3);
        // (3 + 1) / (4 + 6) = 40%. 기간 목표 진행률의 평균(50%)이 아니라 투두 합계 기준입니다.
        assertThat(node.progressRate()).isEqualByComparingTo("40.0");

        GoalTreeResponse.MilestoneNode empty = tree.milestones().get(1);
        assertThat(empty.periodGoals()).isEmpty();
        assertThat(empty.progressRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void 투두가_없는_기간_목표는_0개로_나온다() {
        Milestone first = milestone(1L, 1);
        PeriodGoal week = periodGoal(10L, first, 1);

        GoalTreeResponse tree = GoalTreeResponse.of(3L, List.of(first), List.of(week), Map.of());

        assertThat(tree.milestones().getFirst().periodGoals().getFirst().todoTotal()).isZero();
    }
}
