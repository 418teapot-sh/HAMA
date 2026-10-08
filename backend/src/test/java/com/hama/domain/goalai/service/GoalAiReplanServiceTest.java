package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.goalai.dto.ReplanResponse;
import com.hama.domain.goalai.service.TimeSlotPlacer.Interval;
import com.hama.domain.todo.entity.Todo;
import com.hama.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoalAiReplanServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 10);

    private static Todo todo(LocalDate date, String start, String end) {
        return Todo.createGoalTask(1L, 1L, null, "LC 파트2", date,
                start == null ? null : LocalTime.parse(start), end == null ? null : LocalTime.parse(end));
    }

    @Test
    void 밀린_투두는_fromDate_의_빈_시간으로_옮기고_비어_있는_투두는_그대로_둔다() {
        Todo kept = todo(FROM, "09:00", "10:00");
        Todo overdue = todo(FROM.minusDays(2), "09:00", "10:00");

        ReplanResponse result = GoalAiReplanService.rearrange(List.of(overdue, kept),
                new TimeSlotPlacer(Map.of()), FROM, END);

        assertThat(result).isEqualTo(new ReplanResponse(1, 0));
        assertThat(kept.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(overdue.getTodoDate()).isEqualTo(FROM);
        assertThat(overdue.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(overdue.getEndTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(overdue.getPostponedCount()).isZero();
    }

    @Test
    void 일정과_겹친_투두는_같은_날_빈_시간으로_옮긴다() {
        Todo conflicted = todo(FROM.plusDays(1), "10:00", "11:00");

        ReplanResponse result = GoalAiReplanService.rearrange(List.of(conflicted),
                new TimeSlotPlacer(Map.of(FROM.plusDays(1), List.of(new Interval(9 * 60, 18 * 60)))), FROM, END);

        assertThat(result.movedCount()).isEqualTo(1);
        assertThat(conflicted.getTodoDate()).isEqualTo(FROM.plusDays(1));
        assertThat(conflicted.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void 시간이_없던_투두는_기본_길이로_두고_자리가_없으면_다음_날로_넘긴다() {
        Todo untimed = todo(FROM, null, null);

        GoalAiReplanService.rearrange(List.of(untimed),
                new TimeSlotPlacer(Map.of(FROM, List.of(new Interval(0, 24 * 60)))), FROM, END);

        assertThat(untimed.getTodoDate()).isEqualTo(FROM.plusDays(1));
        assertThat(untimed.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(untimed.getEndTime()).isEqualTo(LocalTime.of(9, GoalAiReplanService.DEFAULT_MINUTES));
    }

    @Test
    void 종료일까지_빈_시간이_없으면_날짜만_두고_unplaced_로_센다() {
        Todo overdue = todo(FROM.minusDays(1), "09:00", "10:00");
        Map<LocalDate, List<Interval>> full = new java.util.HashMap<>();
        for (LocalDate day = FROM; !day.isAfter(END); day = day.plusDays(1)) {
            full.put(day, List.of(new Interval(0, 24 * 60)));
        }

        ReplanResponse result = GoalAiReplanService.rearrange(List.of(overdue), new TimeSlotPlacer(full), FROM, END);

        assertThat(result).isEqualTo(new ReplanResponse(1, 1));
        assertThat(overdue.getTodoDate()).isEqualTo(FROM);
        assertThat(overdue.getStartTime()).isNull();
    }

    @Test
    void 완료한_투두는_재배치할_수_없다() {
        Todo done = todo(FROM, "09:00", "10:00");
        done.complete(LocalDateTime.of(2026, 10, 1, 10, 0));

        assertThatThrownBy(() -> done.reschedule(FROM.plusDays(1), null, null))
                .isInstanceOf(BusinessException.class);
    }
}
