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
import com.hama.domain.payment.service.CreditService;
import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiErrorCode;
import com.hama.global.ai.AiMessage;
import com.hama.global.ai.AiRequest;
import com.hama.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * END 체크인 뒤 목표 전체를 돌아보는 AI 리뷰. 목표당 1건이고, 다시 부르면 기존 리뷰와 목표의 resultStatus 를 덮어씁니다.
 * AI 호출(최대 수십 초) 동안 DB 커넥션을 잡지 않도록 읽기 → AI 호출 → 쓰기를 트랜잭션 두 개로 나눕니다.
 * 호출마다 크레딧 1개를 쓰고, 자격 검증을 통과한 뒤 실패(AI 실패·충돌)하면 돌려줍니다.
 */
@Service
public class GoalAiReviewService {

    private static final int MAX_TEXT = 500;
    private static final int MAX_NOTE_LENGTH = 300;
    private static final int MAX_MID_CHECKINS = 20;

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
    private final CreditService credits;
    private final GoalAiJson json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public GoalAiReviewService(GoalRepository goals, CheckinRepository checkins, GoalReviewRepository reviews,
            GoalTodoRepository goalTodos, AiClient aiClient, CreditService credits, GoalAiJson json,
            TransactionTemplate transaction, @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.checkins = checkins;
        this.reviews = reviews;
        this.goalTodos = goalTodos;
        this.aiClient = aiClient;
        this.credits = credits;
        this.json = json;
        this.transaction = transaction;
        this.clock = clock;
    }

    public ReviewResponse create(Long userId, Long goalId, String currentStatus) {
        LocalDate today = LocalDate.now(clock);
        // 자격 확인 뒤 바로 크레딧을 쓴다. 검증에서 던지면 트랜잭션이 롤백되어 차감도 같이 취소된다(실패 요청은 공짜).
        ReviewPayload payload = transaction.execute(status -> {
            Goal goal = requireReviewable(GoalAiPlanService.owned(goals.findActive(goalId), userId), today);
            credits.use(userId);
            return payload(goal, currentStatus);
        });

        try {
            ReviewAnswer answer = aiClient.chatForJson(AiRequest.of("goal-review", REVIEW_PROMPT.formatted(today),
                    List.of(AiMessage.user(json.write(payload)))), ReviewAnswer.class);
            boolean achieved = validate(answer);
            String comparison = cut(answer.comparison());
            String message = cut(answer.message());

            return transaction.execute(status -> {
                Goal goal = requireReviewable(GoalAiPlanService.owned(goals.findActiveForUpdate(goalId), userId),
                        today);
                // AI 가 답하는 동안(최대 수십 초) 목표나 체크인이 바뀌었으면 옛 데이터로 낸 판정을 버린다.
                if (!payload(goal, currentStatus).equals(payload)) {
                    throw new BusinessException(GoalAiErrorCode.AI_REVIEW_OUTDATED);
                }
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
        } catch (RuntimeException e) {
            // 크레딧을 쓴 뒤 아무 리뷰도 만들지 못했으니 돌려준다(AI 실패, 응답 판정 실패, 충돌 모두 포함).
            transaction.executeWithoutResult(status -> credits.giveBack(userId));
            throw e;
        }
    }

    public ReviewResponse get(Long userId, Long goalId) {
        return transaction.execute(status -> {
            Goal goal = GoalAiPlanService.owned(goals.findActive(goalId), userId);
            GoalReview review = reviews.findByGoalId(goalId)
                    .orElseThrow(() -> new BusinessException(GoalAiErrorCode.AI_REVIEW_NOT_FOUND));
            return ReviewResponse.from(goal, review);
        });
    }

    /** 계획 중(PLANNING) → 종료일 미도달 → END 체크인 없음 순서로 막습니다. 종료 전이면 END 유무와 무관하게 먼저 걸립니다. */
    private Goal requireReviewable(Goal goal, LocalDate today) {
        if (goal.getStatus() == GoalStatus.PLANNING) {
            throw new BusinessException(GoalAiErrorCode.AI_REVIEW_GOAL_NOT_STARTED);
        }
        if (!goal.isPast(today)) {
            throw new BusinessException(GoalAiErrorCode.AI_REVIEW_GOAL_NOT_ENDED);
        }
        if (!checkins.existsByGoalIdAndType(goal.getId(), CheckinType.END)) {
            throw new BusinessException(GoalAiErrorCode.AI_REVIEW_END_REQUIRED);
        }
        return goal;
    }

    private ReviewPayload payload(Goal goal, String currentStatus) {
        List<GoalCheckin> all = checkins.findByGoalIdOrderByCheckedAtAscIdAsc(goal.getId());
        List<CheckinSnapshot> snapshots = limitMid(all, MAX_MID_CHECKINS).stream().map(CheckinSnapshot::from)
                .toList();
        BigDecimal progressRate = goalTodos.countByGoal(goal.getId()).progressRate();
        return new ReviewPayload(goal.getTitle(), goal.getMetricName(), goal.getUnit(), goal.getStartValue(),
                goal.getTargetValue(), progressRate, snapshots, currentStatus);
    }

    /** MID 는 개수 제한이 없어 토큰 한도를 넘길 수 있습니다. 최근 maxMid 개만 남기고, START/END 는 항상 남깁니다. 순서는 그대로입니다. */
    static List<GoalCheckin> limitMid(List<GoalCheckin> all, int maxMid) {
        List<GoalCheckin> mids = all.stream().filter(c -> c.getType() == CheckinType.MID).toList();
        if (mids.size() <= maxMid) {
            return all;
        }
        Set<GoalCheckin> keep = new HashSet<>(mids.subList(mids.size() - maxMid, mids.size()));
        return all.stream().filter(c -> c.getType() != CheckinType.MID || keep.contains(c)).toList();
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
            String note = checkin.getNote();
            String trimmedNote = note != null && note.length() > MAX_NOTE_LENGTH ? note.substring(0, MAX_NOTE_LENGTH)
                    : note;
            return new CheckinSnapshot(checkin.getType().name(), checkin.getValue(), trimmedNote,
                    checkin.getAchieved(), checkin.getCheckedAt());
        }
    }
}
