package com.hama.domain.goal.service;

import com.hama.domain.goal.dto.CreateGoalRequest;
import com.hama.domain.goal.dto.CurrentStatusResponse;
import com.hama.domain.goal.dto.GoalResponses;
import com.hama.domain.goal.dto.UpdateGoalRequest;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalFixedSchedule;
import com.hama.domain.goal.entity.GoalListFilter;
import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalFixedScheduleRepository;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.GoalTodoCount;
import com.hama.domain.goal.repository.GoalTodoRepository;
import com.hama.domain.goalai.repository.GoalPlanRepository;
import com.hama.domain.goalai.service.GoalProgressChanged;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import com.hama.global.response.PageResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalFixedScheduleRepository fixedScheduleRepository;
    private final GoalPlanRepository goalPlanRepository;
    private final GoalTodoRepository goalTodoRepository;
    private final TodoRepository todoRepository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public GoalService(GoalRepository goalRepository, GoalFixedScheduleRepository fixedScheduleRepository,
            GoalPlanRepository goalPlanRepository, GoalTodoRepository goalTodoRepository,
            TodoRepository todoRepository, @Qualifier("be3Clock") Clock clock, ApplicationEventPublisher events) {
        this.goalRepository = goalRepository;
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.goalPlanRepository = goalPlanRepository;
        this.goalTodoRepository = goalTodoRepository;
        this.todoRepository = todoRepository;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public GoalResponses.Created create(Long userId, CreateGoalRequest request) {
        Goal goal = goalRepository.save(Goal.create(userId, request.toContent(), today()));
        if (request.fixedSchedules() != null && !request.fixedSchedules().isEmpty()) {
            requireDistinctDays(request.fixedSchedules());
            fixedScheduleRepository.saveAll(request.fixedSchedules().stream()
                    .map(fixed -> GoalFixedSchedule.create(goal.getId(), fixed.dayOfWeek(), fixed.content()))
                    .toList());
        }
        return new GoalResponses.Created(goal.getId(), goal.effectiveStatus(today()));
    }

    public PageResponse<GoalResponses.Summary> list(Long userId, GoalListFilter filter, Pageable pageable) {
        LocalDate today = today();
        Page<Goal> goals = filter == null ? goalRepository.findListed(userId, pageable)
                : switch (filter) {
                    case IN_PROGRESS -> goalRepository.findInProgress(userId, today, pageable);
                    case PAST -> goalRepository.findPast(userId, today, pageable);
                };
        List<Long> goalIds = goals.getContent().stream().map(Goal::getId).toList();
        Map<Long, GoalTodoCount> counts = goalTodoRepository.countByGoals(goalIds);
        Set<Long> aiApplied = goalPlanRepository.findGoalIdsBySelectedTrueAndGoalIdIn(goalIds);
        return PageResponse.from(goals, goal -> GoalResponses.Summary.from(goal, today,
                counts.getOrDefault(goal.getId(), GoalTodoCount.EMPTY).progressRate(),
                aiApplied.contains(goal.getId())));
    }

    public GoalResponses.Detail get(Long userId, Long goalId) {
        Goal goal = owned(goalRepository.findActive(goalId), userId);
        return detail(goal);
    }

    @Transactional
    public GoalResponses.Detail update(Long userId, Long goalId, UpdateGoalRequest request) {
        Goal goal = owned(goalRepository.findActiveForUpdate(goalId), userId);
        goal.revise(request.mergeInto(goal.content()), today());
        events.publishEvent(new GoalProgressChanged(goalId));
        return detail(goal);
    }

    @Transactional
    public CurrentStatusResponse updateCurrentStatus(Long userId, Long goalId, String currentStatus) {
        Goal goal = owned(goalRepository.findActiveForUpdate(goalId), userId);
        goal.updateCurrentStatus(currentStatus, today());
        return new CurrentStatusResponse(goal.getId(), goal.getCurrentStatus(), goal.getUpdatedAt());
    }

    /** 지난 목표와 PLANNING 목표만 삭제할 수 있고, 연결된 투두도 함께 소프트 삭제합니다. */
    @Transactional
    public void delete(Long userId, Long goalId) {
        Goal goal = owned(goalRepository.findActiveForUpdate(goalId), userId);
        if (!goal.isDeletable(today())) {
            throw new BusinessException(GoalErrorCode.GOAL_NOT_DELETABLE);
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        goal.delete(now);
        // SQL UPDATE 로 지우면 같은 트랜잭션에서 이미 불러온 Todo 가 flush 때 deleted_at 을 되돌립니다.
        todoRepository.findByGoalIdAndDeletedAtIsNull(goal.getId()).forEach(todo -> todo.delete(now));
        events.publishEvent(new GoalProgressChanged(goalId));
    }

    private void requireDistinctDays(List<CreateGoalRequest.FixedSchedule> fixedSchedules) {
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (CreateGoalRequest.FixedSchedule fixed : fixedSchedules) {
            if (!seen.add(fixed.dayOfWeek())) {
                throw new BusinessException(GoalErrorCode.GOAL_INVALID_INPUT);
            }
        }
    }

    private GoalResponses.Detail detail(Goal goal) {
        boolean aiPlanApplied = goalPlanRepository.existsByGoalIdAndSelectedTrue(goal.getId());
        List<GoalFixedSchedule> fixedSchedules = fixedScheduleRepository.findByGoalIdOrderByDayOfWeekAsc(goal.getId());
        return GoalResponses.Detail.from(goal, today(),
                goalTodoRepository.countByGoal(goal.getId()).progressRate(), aiPlanApplied, fixedSchedules);
    }

    private Goal owned(Optional<Goal> found, Long userId) {
        Goal goal = found.orElseThrow(() -> new BusinessException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return goal;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
