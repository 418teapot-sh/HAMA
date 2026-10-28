package com.hama.domain.goalai.service;

import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.GoalTodoCount;
import com.hama.domain.goal.repository.GoalTodoRepository;
import com.hama.domain.goalai.dto.GoalTaskResponses;
import com.hama.domain.goalai.dto.PlanDetail;
import com.hama.domain.goalai.repository.GoalPlanRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 선택된 플랜의 반복 할 일과 할 일별 AI 투두 진행. 홈 목표 카드(이번 주)와 리포트 체크 목록(전체)에 씁니다. */
@Service
@Transactional(readOnly = true)
public class GoalTaskService {

    private final GoalRepository goals;
    private final GoalPlanRepository plans;
    private final GoalTodoRepository goalTodos;
    private final GoalAiJson json;
    private final Clock clock;

    public GoalTaskService(GoalRepository goals, GoalPlanRepository plans, GoalTodoRepository goalTodos,
            GoalAiJson json, @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.plans = plans;
        this.goalTodos = goalTodos;
        this.json = json;
        this.clock = clock;
    }

    public GoalTaskResponses.TaskList tasks(Long userId, Long goalId) {
        GoalAiPlanService.owned(goals.findActive(goalId), userId);
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<PlanDetail.TaskTemplate> templates = plans.findFirstByGoalIdAndSelectedTrue(goalId)
                .map(plan -> json.read(plan.getDetailJson(), PlanDetail.class))
                .map(PlanDetail::tasks)
                .orElse(List.of());
        if (templates.isEmpty()) {
            return new GoalTaskResponses.TaskList(goalId, weekStart, weekEnd, List.of());
        }
        Map<String, GoalTodoCount> all = goalTodos.countByContent(goalId, null, null);
        Map<String, GoalTodoCount> week = goalTodos.countByContent(goalId, weekStart, weekEnd);
        return new GoalTaskResponses.TaskList(goalId, weekStart, weekEnd, templates.stream()
                .map(task -> {
                    GoalTodoCount total = all.getOrDefault(task.content(), GoalTodoCount.EMPTY);
                    GoalTodoCount thisWeek = week.getOrDefault(task.content(), GoalTodoCount.EMPTY);
                    return new GoalTaskResponses.TaskProgress(task.content(), task.timesPerWeek(), task.minutes(),
                            total.total(), total.completed(), thisWeek.total(), thisWeek.completed());
                })
                .toList());
    }
}
