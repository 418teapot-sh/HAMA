package com.hama.domain.goal.service;

import com.hama.domain.goal.dto.CreateGoalRequest;
import com.hama.domain.goal.dto.CurrentStatusResponse;
import com.hama.domain.goal.dto.FixedScheduleResponses;
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
import com.hama.domain.payment.service.CreditService;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import com.hama.global.response.PageResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
    private final CreditService credits;

    public GoalService(GoalRepository goalRepository, GoalFixedScheduleRepository fixedScheduleRepository,
            GoalPlanRepository goalPlanRepository, GoalTodoRepository goalTodoRepository,
            TodoRepository todoRepository, @Qualifier("be3Clock") Clock clock, ApplicationEventPublisher events,
            CreditService credits) {
        this.goalRepository = goalRepository;
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.goalPlanRepository = goalPlanRepository;
        this.goalTodoRepository = goalTodoRepository;
        this.todoRepository = todoRepository;
        this.clock = clock;
        this.events = events;
        this.credits = credits;
    }

    /** 크레딧은 플랜 선택 때 쓰지만, 0이면 플랜을 고를 수 없는 PLANNING 목표만 남으므로 생성 단계에서 막습니다. */
    @Transactional
    public GoalResponses.Created create(Long userId, CreateGoalRequest request) {
        credits.requireAvailable(userId);
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
                    case COMPLETED -> goalRepository.findCompleted(userId, pageable);
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

    /** 기간이 [from, to] 와 겹치는 목표의 요일별 고정 일정을 실제 날짜로 펼쳐, 일정이 있는 날짜만 돌려줍니다. */
    public FixedScheduleResponses.Days fixedSchedules(Long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_RANGE);
        }
        Map<Long, Goal> goals = new LinkedHashMap<>();
        goalRepository.findOverlapping(userId, from, to).forEach(goal -> goals.put(goal.getId(), goal));
        if (goals.isEmpty()) {
            return new FixedScheduleResponses.Days(List.of());
        }
        Map<DayOfWeek, List<GoalFixedSchedule>> byDay = new EnumMap<>(DayOfWeek.class);
        fixedScheduleRepository.findByGoalIdIn(goals.keySet()).stream()
                .sorted(Comparator.comparing(GoalFixedSchedule::getGoalId))
                .forEach(fixed -> byDay.computeIfAbsent(fixed.getDayOfWeek(), day -> new ArrayList<>()).add(fixed));

        List<FixedScheduleResponses.Day> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate current = date;
            List<FixedScheduleResponses.Item> items = byDay.getOrDefault(date.getDayOfWeek(), List.of()).stream()
                    .filter(fixed -> covers(goals.get(fixed.getGoalId()), current))
                    .map(fixed -> new FixedScheduleResponses.Item(fixed.getGoalId(),
                            goals.get(fixed.getGoalId()).getTitle(), fixed.getDayOfWeek(), fixed.getContent()))
                    .toList();
            if (!items.isEmpty()) {
                days.add(new FixedScheduleResponses.Day(date, items));
            }
        }
        return new FixedScheduleResponses.Days(days);
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
        requireAiPlanApplied(goalId);
        goal.updateCurrentStatus(currentStatus, today());
        return new CurrentStatusResponse(goal.getId(), goal.getCurrentStatus(), goal.getUpdatedAt());
    }

    /** AI 플랜을 적용한 진행 중 목표를 COMPLETED 로 바꿉니다. 응답은 상세 조회와 같습니다. */
    @Transactional
    public GoalResponses.Detail complete(Long userId, Long goalId) {
        Goal goal = owned(goalRepository.findActiveForUpdate(goalId), userId);
        requireAiPlanApplied(goalId);
        goal.complete(today());
        events.publishEvent(new GoalProgressChanged(goalId));
        return detail(goal);
    }

    /** 지난 목표·완료한 목표·PLANNING 목표만 삭제할 수 있고, 연결된 투두도 함께 소프트 삭제합니다. */
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

    private static boolean covers(Goal goal, LocalDate date) {
        return !date.isBefore(goal.getStartDate()) && !date.isAfter(goal.getEndDate());
    }

    private void requireAiPlanApplied(Long goalId) {
        if (!goalPlanRepository.existsByGoalIdAndSelectedTrue(goalId)) {
            throw new BusinessException(GoalErrorCode.GOAL_AI_PLAN_NOT_APPLIED);
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
