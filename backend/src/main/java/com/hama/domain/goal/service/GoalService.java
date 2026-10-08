package com.hama.domain.goal.service;

import com.hama.domain.goal.dto.CreateGoalRequest;
import com.hama.domain.goal.dto.GoalResponses;
import com.hama.domain.goal.dto.GoalTreeResponse;
import com.hama.domain.goal.dto.UpdateGoalRequest;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalListFilter;
import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.GoalTodoCount;
import com.hama.domain.goal.repository.GoalTodoRepository;
import com.hama.domain.goal.repository.MilestoneRepository;
import com.hama.domain.goal.repository.PeriodGoalRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import com.hama.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;
    private final MilestoneRepository milestoneRepository;
    private final PeriodGoalRepository periodGoalRepository;
    private final GoalTodoRepository goalTodoRepository;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, MilestoneRepository milestoneRepository,
            PeriodGoalRepository periodGoalRepository, GoalTodoRepository goalTodoRepository,
            @Qualifier("goalClock") Clock clock) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.periodGoalRepository = periodGoalRepository;
        this.goalTodoRepository = goalTodoRepository;
        this.clock = clock;
    }

    @Transactional
    public GoalResponses.Created create(Long userId, CreateGoalRequest request) {
        Goal goal = goalRepository.save(Goal.createDirect(userId, request.toContent(), today()));
        return new GoalResponses.Created(goal.getId(), goal.effectiveStatus(today()));
    }

    public PageResponse<GoalResponses.Summary> list(Long userId, GoalListFilter filter, Pageable pageable) {
        LocalDate today = today();
        Page<Goal> goals = filter == null ? goalRepository.findListed(userId, pageable)
                : switch (filter) {
                    case IN_PROGRESS -> goalRepository.findInProgress(userId, today, pageable);
                    case PAST -> goalRepository.findPast(userId, today, pageable);
                };
        Map<Long, GoalTodoCount> counts = goalTodoRepository.countByGoals(
                goals.getContent().stream().map(Goal::getId).toList());
        return PageResponse.from(goals, goal -> GoalResponses.Summary.from(goal, today,
                counts.getOrDefault(goal.getId(), GoalTodoCount.EMPTY).progressRate()));
    }

    public GoalResponses.Detail get(Long userId, Long goalId) {
        Goal goal = owned(goalRepository.findActive(goalId), userId);
        return detail(goal);
    }

    /** 목표 → 마일스톤 → 기간 목표 계층. 쿼리는 마일스톤·기간 목표·투두 집계 3번입니다. */
    public GoalTreeResponse tree(Long userId, Long goalId) {
        Goal goal = owned(goalRepository.findActive(goalId), userId);
        return GoalTreeResponse.of(goal.getId(),
                milestoneRepository.findByGoalIdOrderBySeqAsc(goal.getId()),
                periodGoalRepository.findByGoalIdOrderByMilestoneIdAscSeqAsc(goal.getId()),
                goalTodoRepository.countByPeriodGoals(goal.getId()));
    }

    @Transactional
    public GoalResponses.Detail update(Long userId, Long goalId, UpdateGoalRequest request) {
        Goal goal = owned(goalRepository.findActiveForUpdate(goalId), userId);
        goal.revise(request.mergeInto(goal.content()), today());
        return detail(goal);
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
        goalTodoRepository.softDeleteByGoal(goal.getId(), now);
    }

    private GoalResponses.Detail detail(Goal goal) {
        return GoalResponses.Detail.from(goal, today(),
                goalTodoRepository.countByGoal(goal.getId()).progressRate());
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
