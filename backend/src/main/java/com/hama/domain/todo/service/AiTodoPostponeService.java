package com.hama.domain.todo.service;

import com.hama.domain.calendar.dto.PostponeDayResponse;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goalai.service.BusyTimeReader;
import com.hama.domain.goalai.service.TimeSlotPlacer;
import com.hama.domain.todo.dto.PostponeTodoRequest;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import com.hama.domain.todo.entity.TodoStatus;
import com.hama.domain.todo.exception.TodoErrorCode;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTodoPostponeService {
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);
    private final TodoRepository todos;
    private final GoalRepository goals;
    private final BusyTimeReader busy;
    private final Clock clock;

    public AiTodoPostponeService(TodoRepository todos, GoalRepository goals, BusyTimeReader busy,
            @Qualifier("be3Clock") Clock clock) {
        this.todos = todos;
        this.goals = goals;
        this.busy = busy;
        this.clock = clock;
    }

    /** 호출자는 소유권 검사 후 목표→투두 순으로 잠근 상태여야 합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postponeLocked(Todo todo, Goal goal, PostponeTodoRequest request) {
        if (todo.getStatus() != TodoStatus.PENDING) throw new BusinessException(TodoErrorCode.TODO_ALREADY_COMPLETED);
        LocalDateTime now = LocalDateTime.now(clock);
        if (goal.effectiveStatus(now.toLocalDate()) != GoalStatus.IN_PROGRESS) {
            throw new BusinessException(TodoErrorCode.TODO_GOAL_NOT_IN_PROGRESS);
        }
        PostponeTodoRequest patch = request == null ? new PostponeTodoRequest() : request;
        if (patch.getTargetDate() != null || patch.hasTimePatch()) {
            manual(todo, goal, patch, now);
            return;
        }
        var slots = new AiPostponeSlots(busy, todo.getUserId(), Set.of(todo.getId()), now);
        if (!automatic(todo, goal, false, now, slots)) throw new BusinessException(TodoErrorCode.TODO_POSTPONE_NO_SLOT);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PostponeDayResponse postponeDay(Long userId, LocalDate date, String strategy) {
        if (!"NEXT_DAY".equals(strategy) && !"NEXT_FREE_SLOT".equals(strategy)) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_INPUT);
        }
        var candidates = todos.findPostponeCandidates(userId, date);
        Map<Long, Goal> lockedGoals = new HashMap<>();
        candidates.stream().map(TodoRepository.PostponeCandidate::getGoalId).distinct().sorted().forEach(id -> {
            goals.findActiveForUpdate(id).ifPresent(goal -> {
                if (!goal.isOwnedBy(userId)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
                lockedGoals.put(id, goal);
            });
        });
        var targets = new ArrayList<Todo>();
        for (var candidate : candidates) {
            if (!lockedGoals.containsKey(candidate.getGoalId())) continue;
            todos.findActiveForUpdate(candidate.getId()).ifPresent(todo -> {
                if (todo.getUserId().equals(userId) && todo.getCategory() == TodoCategory.AI_GOAL_TASK
                        && todo.getStatus() == TodoStatus.PENDING && todo.getTodoDate().equals(date)
                        && todo.getGoalId().equals(candidate.getGoalId())) targets.add(todo);
            });
        }
        targets.sort(Comparator.comparing(Todo::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Todo::getId));
        LocalDateTime now = LocalDateTime.now(clock);
        var slots = new AiPostponeSlots(busy, userId, targets.stream().map(Todo::getId).collect(Collectors.toSet()), now);
        var moved = new ArrayList<PostponeDayResponse.Moved>();
        var unplaced = new ArrayList<PostponeDayResponse.Unplaced>();
        for (Todo todo : targets) {
            Goal goal = lockedGoals.get(todo.getGoalId());
            if (goal.effectiveStatus(now.toLocalDate()) != GoalStatus.IN_PROGRESS) {
                unplaced.add(new PostponeDayResponse.Unplaced(todo.getId(), TodoErrorCode.TODO_GOAL_NOT_IN_PROGRESS.name()));
                continue;
            }
            LocalDate fromDate = todo.getTodoDate();
            LocalDateTime from = todo.getStartTime() == null ? null : fromDate.atTime(todo.getStartTime());
            if (automatic(todo, goal, "NEXT_DAY".equals(strategy), now, slots)) {
                moved.add(new PostponeDayResponse.Moved(todo.getId(), fromDate, from,
                        todo.getTodoDate().atTime(todo.getStartTime())));
            } else {
                unplaced.add(new PostponeDayResponse.Unplaced(todo.getId(), TodoErrorCode.TODO_POSTPONE_NO_SLOT.name()));
            }
        }
        return new PostponeDayResponse(moved.size(), List.copyOf(moved), List.copyOf(unplaced));
    }

    private void manual(Todo todo, Goal goal, PostponeTodoRequest patch, LocalDateTime now) {
        LocalDate target = patch.getTargetDate();
        if (target == null || !target.isAfter(todo.getTodoDate()) || target.isAfter(MAX_DATE) || target.getYear() < 1000) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_POSTPONE_DATE);
        }
        if (target.isBefore(now.toLocalDate()) || goal.getStartDate() == null || goal.getEndDate() == null
                || target.isBefore(goal.getStartDate()) || target.isAfter(goal.getEndDate())) {
            throw new BusinessException(TodoErrorCode.TODO_POSTPONE_OUTSIDE_GOAL);
        }
        LocalTime start = patch.startOr(todo.getStartTime()), end = patch.endOr(todo.getEndTime());
        // 엔티티 검증을 먼저 적용하되 실패 시 전체 트랜잭션이 롤백됩니다.
        todo.postpone(target, start, end);
        if (start != null) {
            if (target.atTime(start).isBefore(now)) throw new BusinessException(TodoErrorCode.TODO_INVALID_TIME);
            var placer = new TimeSlotPlacer(busy.read(todo.getUserId(), target, target, Set.of(todo.getId())));
            if (!placer.reserve(target, start, end)) throw new BusinessException(TodoErrorCode.TODO_POSTPONE_TIME_CONFLICT);
        }
    }

    private boolean automatic(Todo todo, Goal goal, boolean nextDayOnly, LocalDateTime now, AiPostponeSlots slots) {
        if (!todo.getTodoDate().isBefore(MAX_DATE) || goal.getStartDate() == null || goal.getEndDate() == null) return false;
        LocalDate first = todo.getTodoDate().plusDays(1);
        if (first.isBefore(now.toLocalDate())) first = now.toLocalDate();
        if (first.isBefore(goal.getStartDate())) first = goal.getStartDate();
        LocalDate last = first.plusDays(nextDayOnly ? 0 : 365);
        if (last.isAfter(MAX_DATE)) last = MAX_DATE;
        if (last.isAfter(goal.getEndDate())) last = goal.getEndDate();
        if (first.isAfter(last)) return false;
        int minutes = todo.getStartTime() == null ? 30 : (int) Duration.between(todo.getStartTime(), todo.getEndTime()).toMinutes();
        if (minutes > 13 * 60) return false;
        slots.load(first, last);
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            var slot = slots.place(day, minutes);
            if (slot != null) {
                todo.postpone(day, slot.start(), slot.end());
                return true;
            }
        }
        return false;
    }
}
