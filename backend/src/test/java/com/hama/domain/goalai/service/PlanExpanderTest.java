package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.goalai.dto.PlanDetail;
import com.hama.domain.goalai.service.PlanExpander.AiMilestone;
import com.hama.domain.goalai.service.PlanExpander.AiPlan;
import com.hama.domain.goalai.service.PlanExpander.AiTask;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanExpanderTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    private static AiPlan plan(AiMilestone... milestones) {
        return new AiPlan("A", "여유형", "주 3일, 하루 1시간.", List.of(milestones));
    }

    @Test
    void 마일스톤은_계획_기간을_빈틈없이_순서대로_나눈다() {
        PlanExpander.Expanded expanded = PlanExpander.expand(plan(
                new AiMilestone("기초", "2026-09-01", "2026-10-31", List.of(new AiTask("LC 파트2", 3, 60))),
                new AiMilestone("실전", "2026-11-01", "2026-11-30", List.of(new AiTask("모의고사", 2, 120)))),
                "여유형", START, START, END);

        List<PlanDetail.MilestonePlan> milestones = expanded.detail().milestones();
        assertThat(milestones).extracting(PlanDetail.MilestonePlan::startDate)
                .containsExactly(START, LocalDate.of(2026, 11, 1));
        assertThat(milestones).extracting(PlanDetail.MilestonePlan::endDate)
                .containsExactly(LocalDate.of(2026, 10, 31), END);
        assertThat(expanded.detail().goalStartDate()).isEqualTo(START);
        assertThat(expanded.detail().goalEndDate()).isEqualTo(END);
    }

    @Test
    void 주간_목표는_7일씩_끊고_짧은_주는_횟수를_줄인다() {
        PlanExpander.Expanded expanded = PlanExpander.expand(plan(
                new AiMilestone("기초", null, "2026-10-31", List.of(new AiTask("LC 파트2", 3, 60))),
                new AiMilestone("실전", null, null, List.of(new AiTask("모의고사", 2, 120)))),
                "여유형", START, START, END);

        List<PlanDetail.WeekPlan> first = expanded.detail().milestones().getFirst().weeks();
        assertThat(first).hasSize(5);
        assertThat(first.getFirst().title()).isEqualTo("기초 1주차");
        assertThat(first.getLast().startDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(first.getLast().endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(first.getFirst().todos()).extracting(PlanDetail.TodoPlan::date)
                .containsExactly(START, START.plusDays(2), START.plusDays(4));
        assertThat(first.getLast().todos()).hasSize(1);

        // 10월: 3회 × 4주 + 1 = 13개(60분), 11~12월(61일): 2회 × 8주 + 1 = 17개(120분)
        assertThat(expanded.totalTodos()).isEqualTo(30);
        assertThat(expanded.avgDailyMinutes()).isEqualTo(Math.round((13 * 60 + 17 * 120) / 30f));
    }

    @Test
    void 할_일마다_시작_요일을_밀어서_첫날에_몰리지_않는다() {
        PlanExpander.Expanded expanded = PlanExpander.expand(plan(
                new AiMilestone("기초", null, null, List.of(new AiTask("단어", 1, 30), new AiTask("문법", 1, 30)))),
                "여유형", START, START, START.plusDays(6));

        assertThat(expanded.detail().milestones().getFirst().weeks().getFirst().todos())
                .extracting(PlanDetail.TodoPlan::date).containsExactly(START, START.plusDays(1));
    }

    @Test
    void 값이_범위를_벗어나면_맞추고_투두_수에_상한을_둔다() {
        PlanExpander.Expanded expanded = PlanExpander.expand(plan(
                new AiMilestone(" ", null, null, List.of(new AiTask("가".repeat(300), 30, 999),
                        new AiTask("b", 7, 1), new AiTask("c", 7, 30), new AiTask("d", 7, 30), new AiTask("e", 7, 30)))),
                "여유형", START, START, START.plusYears(3));

        PlanDetail.MilestonePlan milestone = expanded.detail().milestones().getFirst();
        assertThat(milestone.title()).isEqualTo("단계 1");
        PlanDetail.TodoPlan first = milestone.weeks().getFirst().todos().getFirst();
        assertThat(first.content()).hasSize(255);
        assertThat(first.minutes()).isEqualTo(PlanExpander.MAX_MINUTES);
        assertThat(milestone.weeks().getFirst().todos()).hasSize(28);
        assertThat(expanded.totalTodos()).isEqualTo(PlanExpander.MAX_TODOS);
    }

    @Test
    void 펼칠_할_일이_없으면_null() {
        assertThat(PlanExpander.expand(plan(new AiMilestone("기초", null, null, List.of())),
                "여유형", START, START, END)).isNull();
        assertThat(PlanExpander.expand(null, "여유형", START, START, END)).isNull();
    }
}
