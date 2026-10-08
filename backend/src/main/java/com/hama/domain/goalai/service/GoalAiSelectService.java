package com.hama.domain.goalai.service;

import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.entity.Milestone;
import com.hama.domain.goal.entity.PeriodGoal;
import com.hama.domain.goal.entity.PeriodType;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.MilestoneRepository;
import com.hama.domain.goal.repository.PeriodGoalRepository;
import com.hama.domain.goalai.dto.PlanDetail;
import com.hama.domain.goalai.dto.SelectResponse;
import com.hama.domain.goalai.entity.GoalPlan;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.goalai.repository.GoalPlanRepository;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고른 플랜대로 마일스톤·주간 목표·투두를 만들고, 투두는 일정과 기존 투두를 피해 빈 시간에 둡니다. */
@Service
public class GoalAiSelectService {

    private final GoalRepository goals;
    private final GoalPlanRepository plans;
    private final MilestoneRepository milestones;
    private final PeriodGoalRepository periodGoals;
    private final TodoRepository todos;
    private final BusyTimeReader busyTimes;
    private final GoalAiJson json;
    private final Clock clock;

    public GoalAiSelectService(GoalRepository goals, GoalPlanRepository plans, MilestoneRepository milestones,
            PeriodGoalRepository periodGoals, TodoRepository todos, BusyTimeReader busyTimes, GoalAiJson json,
            @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.plans = plans;
        this.milestones = milestones;
        this.periodGoals = periodGoals;
        this.todos = todos;
        this.busyTimes = busyTimes;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public SelectResponse select(Long userId, Long planId) {
        LocalDate today = LocalDate.now(clock);
        GoalPlan plan = plans.findById(planId)
                .orElseThrow(() -> new BusinessException(GoalAiErrorCode.AI_PLAN_NOT_FOUND));
        Goal goal = goals.findActiveForUpdate(plan.getGoalId())
                .orElseThrow(() -> new BusinessException(GoalAiErrorCode.AI_PLAN_NOT_FOUND));
        if (!goal.isOwnedBy(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        if (plan.isSelected()) {
            throw new BusinessException(GoalAiErrorCode.AI_PLAN_ALREADY_SELECTED);
        }
        if (goal.getStatus() != GoalStatus.PLANNING) {
            throw new BusinessException(GoalAiErrorCode.GOAL_NOT_PLANNING);
        }
        PlanDetail detail = json.read(plan.getDetailJson(), PlanDetail.class);
        LocalDate planStart = detail.milestones().getFirst().startDate();
        if (!detail.goalStartDate().equals(goal.getStartDate()) || !detail.goalEndDate().equals(goal.getEndDate())
                || planStart.isBefore(today)) {
            throw new BusinessException(GoalAiErrorCode.AI_PLAN_OUTDATED);
        }

        TimeSlotPlacer placer = new TimeSlotPlacer(busyTimes.read(userId, planStart, detail.goalEndDate()));
        int periodGoalCount = 0;
        List<Todo> created = new ArrayList<>();
        List<SelectResponse.UnplacedTodo> unplaced = new ArrayList<>();
        for (PlanDetail.MilestonePlan milestonePlan : detail.milestones()) {
            Milestone milestone = milestones.save(Milestone.create(goal.getId(), milestonePlan.seq(),
                    milestonePlan.title(), null, milestonePlan.startDate(), milestonePlan.endDate()));
            for (PlanDetail.WeekPlan week : milestonePlan.weeks()) {
                PeriodGoal periodGoal = periodGoals.save(PeriodGoal.create(milestone, PeriodType.WEEKLY, week.seq(),
                        week.title(), week.startDate(), week.endDate()));
                periodGoalCount++;
                for (PlanDetail.TodoPlan todo : week.todos()) {
                    TimeSlotPlacer.Slot slot = placer.place(todo.date(), todo.minutes());
                    if (slot == null) {
                        unplaced.add(new SelectResponse.UnplacedTodo(todo.content(), todo.date()));
                    }
                    created.add(Todo.createGoalTask(userId, goal.getId(), periodGoal.getId(), todo.content(),
                            todo.date(), slot == null ? null : slot.start(), slot == null ? null : slot.end()));
                }
            }
        }
        todos.saveAll(created);
        goal.startPlan();
        plan.select();

        return new SelectResponse(goal.getId(), goal.getStatus(),
                new SelectResponse.Created(detail.milestones().size(), periodGoalCount, created.size()),
                new SelectResponse.Placement(created.size() - unplaced.size(), unplaced.size(), unplaced));
    }
}
