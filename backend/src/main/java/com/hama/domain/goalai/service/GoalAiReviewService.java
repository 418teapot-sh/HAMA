package com.hama.domain.goalai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import com.hama.domain.checkin.repository.CheckinRepository;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.GoalTodoRepository;
import com.hama.domain.goalai.dto.ReviewResponse;
import com.hama.domain.goalai.entity.GoalReview;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.goalai.repository.GoalReviewRepository;
import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiErrorCode;
import com.hama.global.ai.AiMessage;
import com.hama.global.ai.AiRequest;
import com.hama.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * END 체크인 뒤 목표 전체를 돌아보는 AI 리뷰. 목표당 1건이고, 다시 부르면 기존 리뷰와 목표의 resultStatus 를 덮어씁니다.
 * AI 호출(최대 수십 초) 동안 DB 커넥션을 잡지 않도록 읽기 → AI 호출 → 쓰기를 트랜잭션 두 개로 나눕니다.
 */
@Service
public class GoalAiReviewService {

    private static final int MAX_TEXT = 500;

    private static final String REVIEW_PROMPT = """
            너는 목표 달성 여부를 판정하고 성과를 비교해 멘트를 주는 코치야. 오늘은 %s(KST)야.
            목표 정보(JSON: 지표 이름·단위·시작값·목표값, 투두 진행률, START/MID/END 체크인 기록, 사용자가 지금 입력한 상태)를 보고 판단해.
            achieved 는 네가 스스로 판정해. END 체크인의 achieved 는 참고만 하고 그대로 따르지 마.
            반드시 아래 필드를 가진 JSON 객체 하나로만 답해.
            {"achieved": true 또는 false,
             "comparison": 시작값·목표값·최종값을 숫자로 비교하는 한국어 문장(500자 이하),
             "message": achieved 가 true 면 칭찬, false 면 격려하는 한국어 문장(500자 이하)}
            """;

    private final GoalRepository goals;
    private final CheckinRepository checkins;
    private final GoalReviewRepository reviews;
    private final GoalTodoRepository goalTodos;
    private final AiClient aiClient;
    private final GoalAiJson json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public GoalAiReviewService(GoalRepository goals, CheckinRepository checkins, GoalReviewRepository reviews,
            GoalTodoRepository goalTodos, AiClient aiClient, GoalAiJson json, TransactionTemplate transaction,
            @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.checkins = checkins;
        this.reviews = reviews;
        this.goalTodos = goalTodos;
        this.aiClient = aiClient;
        this.json = json;
        this.transaction = transaction;
        this.clock = clock;
    }

    public ReviewResponse create(Long userId, Long goalId, String currentStatus) {
        LocalDate today = LocalDate.now(clock);
        ReviewPayload payload = transaction.execute(status ->
                payload(requireReviewable(GoalAiPlanService.owned(goals.findActive(goalId), userId)), currentStatus));

        ReviewAnswer answer = aiClient.chatForJson(AiRequest.of("goal-review", REVIEW_PROMPT.formatted(today),
                List.of(AiMessage.user(json.write(payload)))), ReviewAnswer.class);
        boolean achieved = validate(answer);
        String comparison = cut(answer.comparison());
        String message = cut(answer.message());

        return transaction.execute(status -> {
            Goal goal = requireReviewable(GoalAiPlanService.owned(goals.findActiveForUpdate(goalId), userId));
            GoalReview review = reviews.findByGoalId(goalId)
                    .map(existing -> {
                        existing.overwrite(achieved, comparison, message, currentStatus);
                        return existing;
                    })
                    .orElseGet(() -> reviews.save(GoalReview.create(goalId, achieved, comparison, message,
                            currentStatus)));
            goal.recordResult(achieved);
            return ReviewResponse.from(goal, review);
        });
    }

    public ReviewResponse get(Long userId, Long goalId) {
        return transaction.execute(status -> {
            Goal goal = GoalAiPlanService.owned(goals.findActive(goalId), userId);
            GoalReview review = reviews.findByGoalId(goalId)
                    .orElseThrow(() -> new BusinessException(GoalAiErrorCode.AI_REVIEW_NOT_FOUND));
            return ReviewResponse.from(goal, review);
        });
    }

    private Goal requireReviewable(Goal goal) {
        if (goal.getStatus() == GoalStatus.PLANNING) {
            throw new BusinessException(GoalAiErrorCode.AI_REVIEW_GOAL_NOT_STARTED);
        }
        if (!checkins.existsByGoalIdAndType(goal.getId(), CheckinType.END)) {
            throw new BusinessException(GoalAiErrorCode.AI_REVIEW_END_REQUIRED);
        }
        return goal;
    }

    private ReviewPayload payload(Goal goal, String currentStatus) {
        List<CheckinSnapshot> snapshots = checkins.findByGoalIdOrderByCheckedAtAscIdAsc(goal.getId()).stream()
                .map(CheckinSnapshot::from).toList();
        BigDecimal progressRate = goalTodos.countByGoal(goal.getId()).progressRate();
        return new ReviewPayload(goal.getTitle(), goal.getMetricName(), goal.getUnit(), goal.getStartValue(),
                goal.getTargetValue(), progressRate, snapshots, currentStatus);
    }

    static boolean validate(ReviewAnswer answer) {
        if (answer == null || answer.achieved() == null || answer.comparison() == null
                || answer.comparison().isBlank() || answer.message() == null || answer.message().isBlank()) {
            throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR,
                    new IllegalStateException("[goal-review] AI 응답에 achieved/comparison/message 가 없습니다."));
        }
        return answer.achieved();
    }

    static String cut(String text) {
        String stripped = text.strip();
        return stripped.length() <= MAX_TEXT ? stripped : stripped.substring(0, MAX_TEXT);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReviewAnswer(Boolean achieved, String comparison, String message) {
    }

    record ReviewPayload(String title, String metricName, String unit, BigDecimal startValue, BigDecimal targetValue,
            BigDecimal progressRate, List<CheckinSnapshot> checkins, String currentStatus) {
    }

    record CheckinSnapshot(String type, BigDecimal value, String note, Boolean achieved, LocalDateTime checkedAt) {
        static CheckinSnapshot from(GoalCheckin checkin) {
            return new CheckinSnapshot(checkin.getType().name(), checkin.getValue(), checkin.getNote(),
                    checkin.getAchieved(), checkin.getCheckedAt());
        }
    }
}
