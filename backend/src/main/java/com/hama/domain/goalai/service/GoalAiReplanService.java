package com.hama.domain.goalai.service;

import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goalai.dto.ReplanResponse;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 진행 중인 목표의 미완료 AI 투두를 fromDate 부터 목표 종료일까지 빈 시간으로 다시 둡니다. */
@Service
public class GoalAiReplanService {

    /** 시간이 없던 투두(빈 시간을 못 찾았던 투두)를 다시 둘 때 쓰는 길이. */
    static final int DEFAULT_MINUTES = 30;

    private final GoalRepository goals;
    private final TodoRepository todos;
    private final BusyTimeReader busyTimes;
    private final Clock clock;

    public GoalAiReplanService(GoalRepository goals, TodoRepository todos, BusyTimeReader busyTimes,
            @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.todos = todos;
        this.busyTimes = busyTimes;
        this.clock = clock;
    }

    @Transactional
    public ReplanResponse replan(Long userId, Long goalId, LocalDate requestedFrom) {
        LocalDate today = LocalDate.now(clock);
        Goal goal = GoalAiPlanService.owned(goals.findActiveForUpdate(goalId), userId);
        if (goal.effectiveStatus(today) != GoalStatus.IN_PROGRESS) {
            throw new BusinessException(GoalAiErrorCode.GOAL_NOT_IN_PROGRESS);
        }
        LocalDate from = requestedFrom == null ? today : requestedFrom;
        if (from.isBefore(today) || from.isBefore(goal.getStartDate()) || from.isAfter(goal.getEndDate())) {
            throw new BusinessException(GoalAiErrorCode.AI_REPLAN_INVALID_DATE);
        }
        List<Todo> targets = todos.findPendingGoalTasksForUpdate(goalId);
        if (targets.isEmpty()) {
            return new ReplanResponse(0, 0);
        }
        // 기간 상한 전에 저장된 긴 목표는 1년 안에서만 찾고, 그 뒤에 있는 투두는 건드리지 않습니다.
        LocalDate end = goal.getEndDate();
        if (!Goal.withinMaxPeriod(from, end)) {
            LocalDate limit = from.plusYears(1).minusDays(1);
            end = limit;
            targets = targets.stream().filter(todo -> !todo.getTodoDate().isAfter(limit)).toList();
        }
        TimeSlotPlacer placer = new TimeSlotPlacer(busyTimes.read(userId, from, end,
                targets.stream().map(Todo::getId).collect(Collectors.toSet())));
        return rearrange(targets, placer, from, end);
    }

    /**
     * fromDate 이후에 정해진 시간이 비어 있는 투두는 그대로 두고, 나머지(밀린 투두, 시간 없는 투두, 일정과 겹친 투두)를
     * 순서대로 max(원래 날짜, fromDate) 부터 하루씩 넘기며 첫 빈 시간에 둡니다. 끝까지 없으면 시간 없이 날짜만 둡니다.
     */
    static ReplanResponse rearrange(List<Todo> targets, TimeSlotPlacer placer, LocalDate from, LocalDate end) {
        List<Todo> moving = new ArrayList<>();
        for (Todo todo : targets) {
            boolean keep = !todo.getTodoDate().isBefore(from) && !todo.getTodoDate().isAfter(end)
                    && todo.getStartTime() != null
                    && placer.reserve(todo.getTodoDate(), todo.getStartTime(), todo.getEndTime());
            if (!keep) {
                moving.add(todo);
            }
        }
        int moved = 0;
        int unplaced = 0;
        for (Todo todo : moving) {
            int minutes = todo.getStartTime() == null ? DEFAULT_MINUTES
                    : (int) Duration.between(todo.getStartTime(), todo.getEndTime()).toMinutes();
            LocalDate first = todo.getTodoDate().isBefore(from) ? from : todo.getTodoDate();
            if (first.isAfter(end)) {
                first = end;
            }
            LocalDate date = first;
            TimeSlotPlacer.Slot slot = null;
            for (LocalDate day = first; !day.isAfter(end) && slot == null; day = day.plusDays(1)) {
                slot = placer.place(day, minutes);
                date = day;
            }
            if (slot == null) {
                date = first;
                unplaced++;
            }
            LocalTime start = slot == null ? null : slot.start();
            LocalTime finish = slot == null ? null : slot.end();
            if (!date.equals(todo.getTodoDate()) || !Objects.equals(start, todo.getStartTime())
                    || !Objects.equals(finish, todo.getEndTime())) {
                todo.reschedule(date, start, finish);
                moved++;
            }
        }
        return new ReplanResponse(moved, unplaced);
    }
}
