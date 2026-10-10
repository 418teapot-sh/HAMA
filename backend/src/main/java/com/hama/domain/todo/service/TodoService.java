package com.hama.domain.todo.service;

import com.hama.domain.todo.dto.PostponeTodoRequest;
import com.hama.domain.todo.dto.CreateTodoRequest;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.PeriodGoalRepository;
import com.hama.domain.goal.repository.GoalTodoRepository;
import java.util.Map;
import java.util.stream.Collectors;
import com.hama.domain.todo.dto.TodoResponses;
import com.hama.domain.todo.dto.UpdateTodoNoteRequest;
import com.hama.domain.todo.dto.UpdateTodoRequest;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import com.hama.domain.todo.exception.TodoErrorCode;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final Clock clock;
    private final GoalRepository goals;
    private final PeriodGoalRepository periods;
    private final GoalTodoRepository counts;
    private final AiTodoPostponeService aiPostpone;

    public TodoService(TodoRepository todoRepository, @Qualifier("be3Clock") Clock clock,
            GoalRepository goals, PeriodGoalRepository periods, GoalTodoRepository counts,
            AiTodoPostponeService aiPostpone) {
        this.todoRepository = todoRepository;
        this.clock = clock;
        this.goals = goals;
        this.periods = periods;
        this.counts = counts;
        this.aiPostpone = aiPostpone;
    }

    public TodoResponses.Daily list(Long userId, LocalDate date, TodoCategory category) {
        LocalDate requestedDate = date == null ? LocalDate.now(clock) : date;
        var todos = todoRepository.findActiveByDate(userId, requestedDate, category);
        var ids = todos.stream().map(Todo::getGoalId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> titles = goals.findAllById(ids).stream()
                .collect(Collectors.toMap(Goal::getId, Goal::getTitle));
        return TodoResponses.Daily.from(requestedDate, todos, titles);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TodoResponses.Created create(Long userId, CreateTodoRequest request) {
        if ("TASK".equals(request.category())) {
            if (request.goalId() != null || request.periodGoalId() != null) {
                throw new BusinessException(TodoErrorCode.TODO_INVALID_INPUT);
            }
            return createTask(userId, request.content(), request.todoDate(), request.startTime(), request.endTime());
        }
        if (!"AI_GOAL_TASK".equals(request.category()) || request.goalId() == null || request.todoDate() == null) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_INPUT);
        }
        Goal goal = lockGoal(userId, request.goalId());
        if (goal.effectiveStatus(LocalDate.now(clock)) != GoalStatus.IN_PROGRESS) {
            throw new BusinessException(TodoErrorCode.TODO_GOAL_NOT_IN_PROGRESS);
        }
        if (goal.getStartDate() == null || goal.getEndDate() == null
                || request.todoDate().isBefore(goal.getStartDate()) || request.todoDate().isAfter(goal.getEndDate())) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_GOAL_PERIOD);
        }
        if (request.periodGoalId() != null) {
            var period = periods.findById(request.periodGoalId())
                    .orElseThrow(() -> new BusinessException(TodoErrorCode.TODO_PERIOD_GOAL_NOT_FOUND));
            if (!period.getGoalId().equals(goal.getId())) {
                Goal parent = goals.findActive(period.getGoalId())
                        .orElseThrow(() -> new BusinessException(GoalErrorCode.GOAL_NOT_FOUND));
                if (!parent.isOwnedBy(userId)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
                throw new BusinessException(TodoErrorCode.TODO_PERIOD_GOAL_MISMATCH);
            }
            if (request.todoDate().isBefore(period.getStartDate()) || request.todoDate().isAfter(period.getEndDate())) {
                throw new BusinessException(TodoErrorCode.TODO_INVALID_GOAL_PERIOD);
            }
        }
        Todo todo = todoRepository.save(Todo.createGoalTask(userId, goal.getId(), request.periodGoalId(),
                request.content(), request.todoDate(), request.startTime(), request.endTime()));
        return new TodoResponses.Created(todo.getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TodoResponses.Created createTask(Long userId, String content, LocalDate date,
            LocalTime startTime, LocalTime endTime) {
        Todo todo = todoRepository.save(Todo.createTask(userId, content, date, startTime, endTime));
        return new TodoResponses.Created(todo.getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TodoResponses.Updated update(Long userId, Long todoId, UpdateTodoRequest request) {
        Todo todo = ownedForUpdate(userId, todoId);
        todo.revise(request.getContent() == null ? todo.getContent() : request.getContent(),
                request.startOr(todo.getStartTime()), request.endOr(todo.getEndTime()));
        return TodoResponses.Updated.from(todo);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TodoResponses.Postponed postpone(Long userId, Long todoId, PostponeTodoRequest request) {
        Todo todo = ownedForUpdate(userId, todoId);
        if (todo.getCategory() == TodoCategory.AI_GOAL_TASK) {
            aiPostpone.postponeLocked(todo, lockGoal(userId, todo.getGoalId()), request);
            return TodoResponses.Postponed.from(todo);
        }
        PostponeTodoRequest patch = request == null ? new PostponeTodoRequest() : request;
        LocalDate target = patch.getTargetDate() == null
                ? todo.getTodoDate().plusDays(1) : patch.getTargetDate();
        todo.postpone(target, patch.startOr(todo.getStartTime()), patch.endOr(todo.getEndTime()));
        return TodoResponses.Postponed.from(todo);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TodoResponses.Completion complete(Long userId, Long todoId) {
        Todo todo = ownedForUpdate(userId, todoId);
        todo.complete(now());
        todoRepository.flush();
        return TodoResponses.Completion.from(todo, todo.getGoalId() == null ? null
                : counts.countByGoal(todo.getGoalId()).progressRate());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TodoResponses.Note updateNote(Long userId, Long todoId, UpdateTodoNoteRequest request) {
        Todo todo = ownedForUpdate(userId, todoId);
        if (request.hasStatusNote()) {
            todo.changeStatusNote(request.getStatusNote());
        }
        return new TodoResponses.Note(todo.getId(), todo.getStatusNote());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long userId, Long todoId) {
        ownedForUpdate(userId, todoId).delete(now());
    }

    private Todo ownedForUpdate(Long userId, Long todoId) {
        // Scalar 조회로 잠금 전 엔티티 스냅샷을 영속성 컨텍스트에 남기지 않습니다.
        var link = todoRepository.findActiveLink(todoId)
                .orElseThrow(() -> new BusinessException(TodoErrorCode.TODO_NOT_FOUND));
        if (!link.getUserId().equals(userId)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        if (link.getGoalId() != null) lockGoal(userId, link.getGoalId());
        Todo todo = todoRepository.findActiveForUpdate(todoId)
                .orElseThrow(() -> new BusinessException(TodoErrorCode.TODO_NOT_FOUND));
        if (!todo.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return todo;
    }

    private Goal lockGoal(Long userId, Long id) {
        Goal goal = goals.findActiveForUpdate(id)
                .orElseThrow(() -> new BusinessException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(userId)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        return goal;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
