package com.hama.domain.goalai.service;

import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.GoalFixedScheduleRepository;
import com.hama.domain.goalai.dto.ReplanResponse;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.payment.service.CreditService;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final GoalFixedScheduleRepository fixedSchedules;
    private final TodoRepository todos;
    private final BusyTimeReader busyTimes;
    private final CreditService credits;
    private final Clock clock;

    public GoalAiReplanService(GoalRepository goals, GoalFixedScheduleRepository fixedSchedules, TodoRepository todos, BusyTimeReader busyTimes,
            CreditService credits, @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.fixedSchedules = fixedSchedules;
        this.todos = todos;
        this.busyTimes = busyTimes;
        this.credits = credits;
        this.clock = clock;
    }

    @Transactional
    public ReplanResponse replan(Long userId, Long goalId, LocalDate requestedFrom) {
        // 재계산에 1회. 목표를 잠그기 전에 쓰고(CreditService 락 순서), 옮긴 게 없으면 아래에서 돌려줍니다.
        credits.use(userId);
        LocalDate today = LocalDate.now(clock);
        Goal goal = GoalAiPlanService.owned(goals.findActiveForUpdate(goalId), userId);
        if (goal.effectiveStatus(today) != GoalStatus.IN_PROGRESS) {
            throw new BusinessException(GoalAiErrorCode.GOAL_NOT_IN_PROGRESS);
        }
        // 시작 전인 목표는 오늘이 아니라 시작일부터 둡니다.
        LocalDate from = requestedFrom != null ? requestedFrom
                : today.isBefore(goal.getStartDate()) ? goal.getStartDate() : today;
        if (from.isBefore(today) || from.isBefore(goal.getStartDate()) || from.isAfter(goal.getEndDate())) {
            throw new BusinessException(GoalAiErrorCode.AI_REPLAN_INVALID_DATE);
        }
        List<Todo> targets = todos.findPendingGoalTasksForUpdate(goalId);
        if (targets.isEmpty()) {
            credits.giveBack(userId);
            return new ReplanResponse(0, 0);
        }
        TimeSlotPlacer placer = new TimeSlotPlacer(busyTimes.read(userId, from, goal.getEndDate(),
                targets.stream().map(Todo::getId).collect(Collectors.toSet())));
        Set<DayOfWeek> busyDays = GoalAiPlanService.days(fixedSchedules.findByGoalIdOrderByDayOfWeekAsc(goalId));
        ReplanResponse result = rearrange(targets, placer, from, goal.getEndDate(), busyDays);
        // 실제로 바뀐 투두가 없으면 차감하지 않습니다.
        if (result.movedCount() == 0) {
            credits.giveBack(userId);
        }
        return result;
    }

    /** 호출자는 이미 목표를 잠갔습니다. 기간 밖 미완료 투두만 무료로 조정합니다. */
    public ReplanResponse adjustForPeriodChange(Long userId, Goal goal) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.isAfter(goal.getStartDate()) ? today : goal.getStartDate();
        List<Todo> targets = todos.findPendingGoalTasksForUpdate(goal.getId()).stream()
                .filter(todo -> todo.getTodoDate().isBefore(goal.getStartDate())
                        || todo.getTodoDate().isAfter(goal.getEndDate()))
                .toList();
        if (targets.isEmpty()) return new ReplanResponse(0, 0);
        TimeSlotPlacer placer = new TimeSlotPlacer(busyTimes.read(userId, from, goal.getEndDate(),
                targets.stream().map(Todo::getId).collect(Collectors.toSet())));
        if (from.equals(today) && !LocalTime.now(clock).equals(LocalTime.MIDNIGHT)) {
            placer.block(today, LocalTime.MIDNIGHT, LocalTime.now(clock));
        }
        Set<DayOfWeek> busyDays = GoalAiPlanService.days(fixedSchedules.findByGoalIdOrderByDayOfWeekAsc(goal.getId()));
        return rearrange(targets, placer, from, goal.getEndDate(), busyDays, true);
    }

    /**
     * fromDate 이후에 정해진 시간이 비어 있는 투두는 그대로 두고, 나머지(밀린 투두, 시간 없는 투두, 일정과 겹친 투두)를
     * 순서대로 max(원래 날짜, fromDate) 부터 하루씩 넘기며 첫 빈 시간에 둡니다. 끝까지 없으면 원래 날짜와 시간을 보존합니다.
     */
    static ReplanResponse rearrange(List<Todo> targets, TimeSlotPlacer placer, LocalDate from, LocalDate end) {
        return rearrange(targets, placer, from, end, Set.of());
    }

    private record Placement(LocalDate date, TimeSlotPlacer.Slot slot) {}

    static ReplanResponse rearrange(List<Todo> targets, TimeSlotPlacer base, LocalDate from, LocalDate end,
            Set<DayOfWeek> busyDays) {
        return rearrange(targets, base, from, end, busyDays, false);
    }

    private static ReplanResponse rearrange(List<Todo> targets, TimeSlotPlacer base, LocalDate from, LocalDate end,
            Set<DayOfWeek> busyDays, boolean restartOutsidePeriod) {
        Set<Todo> failed = new HashSet<>();
        Map<Todo, Placement> results;
        // 실패 원본을 점유에 포함하면 앞서 배치했던 투두도 영향을 받을 수 있으므로 안정될 때까지 다시 계산합니다.
        while (true) {
            TimeSlotPlacer placer = base.copy();
            for (Todo todo : failed) {
                if (todo.getStartTime() != null) placer.block(todo.getTodoDate(), todo.getStartTime(), todo.getEndTime());
            }
            results = new LinkedHashMap<>();
            Set<Todo> nextFailed = new HashSet<>(failed);
            List<Todo> moving = new ArrayList<>();
            for (Todo todo : targets) {
                if (failed.contains(todo)) continue;
                boolean keep = !todo.getTodoDate().isBefore(from) && !todo.getTodoDate().isAfter(end)
                        && !busyDays.contains(todo.getTodoDate().getDayOfWeek()) && todo.getStartTime() != null
                        && placer.reserve(todo.getTodoDate(), todo.getStartTime(), todo.getEndTime());
                if (!keep) moving.add(todo);
            }
            for (Todo todo : moving) {
                int minutes = todo.getStartTime() == null ? DEFAULT_MINUTES
                        : (int) Duration.between(todo.getStartTime(), todo.getEndTime()).toMinutes();
                LocalDate first = todo.getTodoDate().isBefore(from) ? from : todo.getTodoDate();
                if (first.isAfter(end)) first = restartOutsidePeriod ? from : end;
                Placement placement = null;
                for (LocalDate day = first; !day.isAfter(end) && placement == null; day = day.plusDays(1)) {
                    if (busyDays.contains(day.getDayOfWeek())) continue;
                    TimeSlotPlacer.Slot slot = placer.place(day, minutes);
                    if (slot != null) placement = new Placement(day, slot);
                }
                if (placement == null) nextFailed.add(todo);
                else results.put(todo, placement);
            }
            if (nextFailed.equals(failed)) break;
            failed = nextFailed;
        }
        int moved = 0;
        for (var entry : results.entrySet()) {
            Todo todo = entry.getKey();
            Placement placement = entry.getValue();
            if (!placement.date().equals(todo.getTodoDate()) || !Objects.equals(placement.slot().start(), todo.getStartTime())
                    || !Objects.equals(placement.slot().end(), todo.getEndTime())) {
                todo.reschedule(placement.date(), placement.slot().start(), placement.slot().end());
                moved++;
            }
        }
        return new ReplanResponse(moved, failed.size());
    }

}
