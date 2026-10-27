package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.goalai.dto.PlanDetail;
import com.hama.domain.goalai.service.PlanExpander.AiPlan;
import com.hama.domain.goalai.service.PlanExpander.AiTask;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanExpanderTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    private static AiPlan plan(AiTask... tasks) {
        return new AiPlan("A", "여유형", "매일 영어 단어 30개씩 외우기", List.of(tasks));
    }

    @Test
    void 마일스톤_없이_기간_전체를_7일씩_끊어_횟수만큼_흩어_둔다() {
        PlanExpander.Expanded expanded = PlanExpander.expand(plan(new AiTask("영어 단어 외우기", 3, 60)),
                "여유형", START, START, END);

        // 92일 = 13주(7일) + 1일. 짧은 마지막 1일짜리 주도 최소 1회는 배치합니다: 13*3 + 1 = 40.
        assertThat(expanded.totalTodos()).isEqualTo(40);
        assertThat(expanded.detail().goalStartDate()).isEqualTo(START);
        assertThat(expanded.detail().goalEndDate()).isEqualTo(END);
        assertThat(expanded.detail().planStartDate()).isEqualTo(START);
        assertThat(expanded.detail().tasks()).containsExactly(new PlanDetail.TaskTemplate("영어 단어 외우기", 3, 60));
        assertThat(expanded.avgDailyMinutes()).isEqualTo(60);
    }

    @Test
    void 짧은_마지막_주는_횟수를_줄이되_최소_1회는_배치한다() {
        LocalDate end = START.plusDays(9); // 10일 = 1주(7일) + 3일짜리 짧은 주
        PlanExpander.Expanded expanded = PlanExpander.expand(plan(new AiTask("영어 단어 외우기", 3, 60)),
                "여유형", START, START, end);

        assertThat(expanded.detail().todos()).extracting(PlanDetail.TodoPlan::date)
                .containsExactly(START, START.plusDays(2), START.plusDays(4), START.plusDays(7));
    }

    @Test
    void 할_일마다_시작_요일을_밀어서_첫날에_몰리지_않는다() {
        PlanExpander.Expanded expanded = PlanExpander.expand(
                plan(new AiTask("단어", 1, 30), new AiTask("문법", 1, 30)),
                "여유형", START, START, START.plusDays(6));

        assertThat(expanded.detail().todos()).extracting(PlanDetail.TodoPlan::date)
                .containsExactly(START, START.plusDays(1));
    }

    @Test
    void 값이_범위를_벗어나면_맞추고_할_일_개수와_투두_수에_상한을_둔다() {
        PlanExpander.Expanded expanded = PlanExpander.expand(plan(
                new AiTask("가".repeat(300), 30, 999), new AiTask("b", 7, 1), new AiTask("c", 7, 30),
                new AiTask("d", 7, 30), new AiTask("e", 7, 30), new AiTask("f(무시됨)", 7, 30)),
                "여유형", START, START, START.plusYears(3));

        assertThat(expanded.detail().tasks()).hasSize(PlanExpander.MAX_TASKS); // 6번째 할 일은 무시
        PlanDetail.TaskTemplate first = expanded.detail().tasks().getFirst();
        assertThat(first.content()).hasSize(255);
        assertThat(first.minutes()).isEqualTo(PlanExpander.MAX_MINUTES);
        assertThat(first.timesPerWeek()).isEqualTo(7); // 30 -> 7로 클램프
        assertThat(expanded.totalTodos()).isEqualTo(PlanExpander.MAX_TODOS);
    }

    @Test
    void 펼칠_할_일이_없으면_null() {
        assertThat(PlanExpander.expand(plan(), "여유형", START, START, END)).isNull();
        assertThat(PlanExpander.expand(new AiPlan("A", "여유형", "-", null), "여유형", START, START, END)).isNull();
        assertThat(PlanExpander.expand(null, "여유형", START, START, END)).isNull();
    }
}
