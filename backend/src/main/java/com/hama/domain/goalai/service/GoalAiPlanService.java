package com.hama.domain.goalai.service;

import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goalai.dto.PlanDetail;
import com.hama.domain.goalai.dto.PlanResponses;
import com.hama.domain.goalai.entity.GoalPlan;
import com.hama.domain.goalai.entity.PlanPreference;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.goalai.repository.GoalPlanRepository;
import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiErrorCode;
import com.hama.global.ai.AiMessage;
import com.hama.global.ai.AiRequest;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** PLANNING 목표의 A(여유형)/B(집중형) 플랜 생성과 조회. */
@Service
public class GoalAiPlanService {

    private static final List<String> VARIANTS = List.of("A", "B");
    private static final List<String> DEFAULT_TITLES = List.of("여유형", "집중형");

    private static final String PLAN_PROMPT = """
            너는 목표를 실행 계획으로 쪼개는 플래너야. 오늘은 %s(KST)야.
            목표 정보(JSON)를 보고 성격이 다른 플랜 두 개를 만들어. A 는 여유형(적은 횟수·짧은 시간), B 는 집중형(많은 횟수·긴 시간).
            사용자 선호: %s.
            마일스톤 2~5개가 planStartDate ~ planEndDate 를 순서대로 나눠야 해. 매주 할 일의 총 시간이 weeklyAvailableHours 를 넘지 않게 해.
            반드시 아래 모양의 JSON 객체 하나로만, 짧게 답해.
            {"plans":[{"variant":"A","title":10자 이하,"summary":"주 5일, 하루 1.5시간." 같은 한 문장,
              "milestones":[{"title":마일스톤 이름,"startDate":"yyyy-MM-dd","endDate":"yyyy-MM-dd",
                "weeklyTasks":[{"content":투두 한 줄,"sessionsPerWeek":1~7,"minutes":10~240}]}]},
             {"variant":"B", ...}]}
            마일스톤마다 weeklyTasks 는 1~3개야.
            """;

    private final GoalRepository goals;
    private final GoalPlanRepository plans;
    private final AiClient aiClient;
    private final GoalAiJson json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public GoalAiPlanService(GoalRepository goals, GoalPlanRepository plans, AiClient aiClient, GoalAiJson json,
            TransactionTemplate transaction, @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.plans = plans;
        this.aiClient = aiClient;
        this.json = json;
        this.transaction = transaction;
        this.clock = clock;
    }

    public PlanResponses.PlanList generate(Long userId, Long goalId, PlanPreference requested) {
        PlanPreference preference = requested == null ? PlanPreference.BALANCED : requested;
        LocalDate today = LocalDate.now(clock);
        Goal goal = transaction.execute(status -> requirePlanning(owned(goals.findActive(goalId), userId)));
        // PLANNING 목표는 수정에서 기간을 지울 수 있습니다. 기간이 없거나 이미 끝났으면 투두를 둘 날이 없습니다.
        if (goal.getStartDate() == null || goal.getEndDate() == null) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_PERIOD);
        }
        // 기간 상한이 생기기 전에 저장된 목표는 1년을 넘을 수 있어서, 펼치기 전에 막습니다.
        if (!Goal.withinMaxPeriod(goal.getStartDate(), goal.getEndDate())) {
            throw new BusinessException(GoalErrorCode.GOAL_PERIOD_TOO_LONG);
        }
        LocalDate planStart = goal.getStartDate().isBefore(today) ? today : goal.getStartDate();
        if (planStart.isAfter(goal.getEndDate())) {
            throw new BusinessException(GoalErrorCode.GOAL_INVALID_PERIOD);
        }

        PlanExpander.AiPlans answer = aiClient.chatForJson(AiRequest.of("goal-plan",
                PLAN_PROMPT.formatted(today, preference.prompt()),
                List.of(AiMessage.user(json.write(goalInfo(goal, planStart))))), PlanExpander.AiPlans.class);
        List<PlanExpander.Expanded> expanded = expandBoth(answer, goal.getStartDate(), planStart, goal.getEndDate());

        return transaction.execute(status -> {
            Goal locked = requirePlanning(owned(goals.findActiveForUpdate(goalId), userId));
            if (!Objects.equals(locked.getStartDate(), goal.getStartDate())
                    || !Objects.equals(locked.getEndDate(), goal.getEndDate())) {
                throw new BusinessException(GoalAiErrorCode.AI_PLAN_OUTDATED);
            }
            plans.deleteUnselected(goalId);
            List<PlanResponses.PlanView> views = new ArrayList<>();
            for (int i = 0; i < VARIANTS.size(); i++) {
                PlanExpander.Expanded plan = expanded.get(i);
                GoalPlan saved = plans.save(GoalPlan.create(goalId, VARIANTS.get(i), plan.title(), plan.summary(),
                        preference, json.write(plan.detail()), plan.totalTodos(), plan.avgDailyMinutes()));
                views.add(PlanResponses.PlanView.of(saved, plan.detail()));
            }
            return new PlanResponses.PlanList(views);
        });
    }

    public PlanResponses.PlanList list(Long userId, Long goalId) {
        return transaction.execute(status -> {
            owned(goals.findActive(goalId), userId);
            return new PlanResponses.PlanList(plans.findByGoalIdOrderByIdAsc(goalId).stream()
                    .map(plan -> PlanResponses.PlanView.of(plan, json.read(plan.getDetailJson(), PlanDetail.class)))
                    .toList());
        });
    }

    static Goal owned(Optional<Goal> found, Long userId) {
        Goal goal = found.orElseThrow(() -> new BusinessException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return goal;
    }

    private static Goal requirePlanning(Goal goal) {
        if (goal.getStatus() != GoalStatus.PLANNING) {
            throw new BusinessException(GoalAiErrorCode.GOAL_NOT_PLANNING);
        }
        return goal;
    }

    private static Map<String, Object> goalInfo(Goal goal, LocalDate planStart) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", goal.getTitle());
        info.put("metricName", goal.getMetricName());
        info.put("unit", goal.getUnit());
        info.put("startValue", goal.getStartValue());
        info.put("targetValue", goal.getTargetValue());
        info.put("currentLevel", goal.getCurrentLevel());
        info.put("weeklyAvailableHours", goal.getWeeklyAvailableHours());
        info.put("planStartDate", planStart.toString());
        info.put("planEndDate", goal.getEndDate().toString());
        return info;
    }

    /** A·B 둘 다 펼칠 수 있어야 합니다. variant 를 안 주거나 순서가 바뀌어도 받고, 없으면 순서대로 A·B 로 봅니다. */
    private static List<PlanExpander.Expanded> expandBoth(PlanExpander.AiPlans answer, LocalDate goalStart,
            LocalDate planStart, LocalDate planEnd) {
        List<PlanExpander.AiPlan> source = answer == null || answer.plans() == null ? List.of() : answer.plans();
        List<PlanExpander.Expanded> result = new ArrayList<>();
        for (int i = 0; i < VARIANTS.size(); i++) {
            String variant = VARIANTS.get(i);
            int index = i;
            PlanExpander.AiPlan plan = source.stream()
                    .filter(p -> p != null && variant.equalsIgnoreCase(p.variant() == null ? "" : p.variant().strip()))
                    .findFirst()
                    .orElse(index < source.size() ? source.get(index) : null);
            PlanExpander.Expanded expanded = PlanExpander.expand(plan, DEFAULT_TITLES.get(i), goalStart,
                    planStart, planEnd);
            if (expanded == null || expanded.totalTodos() == 0) {
                throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR,
                        new IllegalStateException("[goal-plan] 플랜 " + variant + " 를 펼칠 수 없습니다."));
            }
            result.add(expanded);
        }
        return result;
    }
}
