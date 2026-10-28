package com.hama.domain.goalai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import com.hama.domain.checkin.repository.CheckinRepository;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goal.repository.GoalTodoRepository;
import com.hama.domain.goalai.dto.ReviewResponse;
import com.hama.domain.goalai.entity.GoalReview;
import com.hama.domain.goalai.entity.GoalReviewGeneration;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.goalai.repository.GoalReviewGenerationRepository;
import com.hama.domain.goalai.repository.GoalReviewRepository;
import com.hama.domain.payment.service.CreditService;
import com.hama.domain.user.exception.UserErrorCode;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiErrorCode;
import com.hama.global.ai.AiMessage;
import com.hama.global.ai.AiRequest;
import com.hama.global.ai.LinerProperties;
import com.hama.global.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * END 체크인 뒤 목표 전체를 돌아보는 AI 리뷰. 목표당 1건이고, 다시 부르면 기존 리뷰와 목표의 resultStatus 를 덮어씁니다.
 * AI 대기 동안 DB 커넥션을 잡지 않습니다. 만료 회수 → 예약 → AI 호출 → 저장은 독립된 짧은 단계입니다.
 * 동일 입력은 무료 재사용합니다. 새 생성은 호출 전에 예약·차감하고 실패/만료 시 예약당 한 번만 환급합니다.
 */
@Slf4j
@Service
public class GoalAiReviewService {

    private static final int MAX_LINE = 40;
    private static final int MAX_NOTE_LENGTH = 300;
    private static final int MAX_MID_CHECKINS = 20;

    private static final String REVIEW_PROMPT = """
            너는 목표 달성 여부를 판정하고 성과를 비교해 멘트를 주는 코치야. 오늘은 %s(KST)야.
            목표 정보(JSON: 목표 제목, 목표를 시작할 때 입력한 현재 상태(initialStatus), 투두 진행률, START/MID/END 체크인 기록,
            지금(종료 시점) 입력한 상태(currentStatus))를 보고 판단해. initialStatus 와 currentStatus 는 자유 텍스트라 숫자가 아닐 수 있어.
            achieved 는 네가 스스로 판정해. END 체크인의 achieved 는 참고만 하고 그대로 따르지 마.
            화면에 두 줄로 보여 주므로 comparison 과 message 는 각각 줄바꿈 없는 짧은 한 문장(%d자 이하)이어야 해.
            반드시 아래 필드를 가진 JSON 객체 하나로만 답해.
            {"achieved": true 또는 false,
             "comparison": initialStatus·currentStatus·투두 진행률을 비교한 한 줄(예: "처음보다 100점 올랐어요!"),
             "message": achieved 가 true 면 칭찬, false 면 격려하는 한 줄(예: "일정을 모두 소화했어요!")}
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
    private final UserRepository users;
    private final GoalReviewGenerationRepository generations;
    private final long leaseSeconds;

    public GoalAiReviewService(GoalRepository goals, CheckinRepository checkins, GoalReviewRepository reviews,
            GoalTodoRepository goalTodos, AiClient aiClient, CreditService credits, GoalAiJson json,
            TransactionTemplate transaction, @Qualifier("be3Clock") Clock clock, UserRepository users,
            GoalReviewGenerationRepository generations, LinerProperties liner) {
        this.goals = goals;
        this.checkins = checkins;
        this.reviews = reviews;
        this.goalTodos = goalTodos;
        this.aiClient = aiClient;
        this.credits = credits;
        this.json = json;
        this.transaction = new TransactionTemplate(transaction.getTransactionManager());
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
        this.users = users;
        this.generations = generations;
        this.leaseSeconds = Math.max(120L, (long) liner.timeoutSeconds() + 60L);
    }

    public Creation create(Long userId, Long goalId, String currentStatus) {
        // 회수는 새 요청의 자격 오류/잔액 부족과 분리해 먼저 커밋합니다.
        recoverExpired(userId, goalId);
        ReadSnapshot snapshot = transaction.execute(status -> reserve(userId, goalId, currentStatus));
        if (snapshot.cached() != null) {
            return new Creation(snapshot.cached(), false);
        }

        try {
            ReviewAnswer answer = aiClient.chatForJson(AiRequest.of("goal-review",
                    REVIEW_PROMPT.formatted(LocalDate.now(clock), MAX_LINE),
                    List.of(AiMessage.user(json.write(snapshot.payload())))), ReviewAnswer.class);
            boolean achieved = validate(answer);
            String comparison = oneLine(answer.comparison());
            String message = oneLine(answer.message());

            return transaction.execute(status -> {
                lockOwner(userId, goalId);
                Goal goal = requireReviewable(GoalAiPlanService.owned(goals.findActive(goalId), userId),
                        LocalDate.now(clock));
                GoalReviewGeneration generation = generations.findByGoalId(goalId)
                        .orElseThrow(() -> new BusinessException(GoalAiErrorCode.AI_REVIEW_OUTDATED));
                if (!generation.owns(snapshot.token()) || generation.expired(LocalDateTime.now(clock))
                        || !fingerprint(payload(goal, currentStatus)).equals(snapshot.inputHash())) {
                    throw new BusinessException(GoalAiErrorCode.AI_REVIEW_OUTDATED);
                }
                Optional<GoalReview> existing = reviews.findByGoalId(goalId);
                if (!Objects.equals(ReviewVersion.of(existing), snapshot.version())) {
                    throw new BusinessException(GoalAiErrorCode.AI_REVIEW_OUTDATED);
                }
                GoalReview review = existing.orElseGet(() -> GoalReview.create(goalId, achieved, comparison,
                        message, currentStatus));
                review.overwrite(achieved, comparison, message, currentStatus);
                reviews.saveAndFlush(review);
                goal.recordResult(achieved);
                generation.complete(snapshot.inputHash(), resultFingerprint(review));
                return new Creation(ReviewResponse.from(goal, review), true);
            });
        } catch (RuntimeException original) {
            try {
                releaseFailed(userId, goalId, snapshot.token());
            } catch (RuntimeException refundFailure) {
                // 환급 UPDATE도 롤백되므로 예약은 남아 다음 만료 회수에서 재시도할 수 있습니다.
                original.addSuppressed(refundFailure);
                log.warn("[goal-review] refund failed goalId={} error={}", goalId,
                        refundFailure.getClass().getSimpleName());
            }
            throw original;
        }
    }

    private ReadSnapshot reserve(Long userId, Long goalId, String currentStatus) {
        lockOwner(userId, goalId);
        Goal goal = requireReviewable(GoalAiPlanService.owned(goals.findActive(goalId), userId), LocalDate.now(clock));
        GoalReviewGeneration generation = generations.findByGoalId(goalId)
                .orElseGet(() -> generations.save(GoalReviewGeneration.create(goalId)));
        // 입력이 달라도 진행 중인 요청의 예약을 빼앗지 않습니다.
        if (generation.reserved()) {
            throw new BusinessException(GoalAiErrorCode.AI_REVIEW_GENERATING);
        }
        ReviewPayload payload = payload(goal, currentStatus);
        String inputHash = fingerprint(payload);
        Optional<GoalReview> existing = reviews.findByGoalId(goalId);
        if (inputHash.equals(generation.getSuccessfulInputHash()) && existing.isPresent()
                && resultFingerprint(existing.get()).equals(generation.getSuccessfulResultHash())) {
            return new ReadSnapshot(payload, ReviewVersion.of(existing), inputHash, null,
                    ReviewResponse.from(goal, existing.get()));
        }
        credits.use(userId);
        String token = UUID.randomUUID().toString();
        generation.reserve(token, inputHash, LocalDateTime.now(clock).plusSeconds(leaseSeconds));
        return new ReadSnapshot(payload, ReviewVersion.of(existing), inputHash, token, null);
    }

    /** 사용자 → 목표 순서. 소프트 삭제 후 환급도 같은 순서로 직렬화합니다. */
    private void lockOwner(Long userId, Long goalId) {
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        Long owner = generations.lockGoalOwner(goalId)
                .orElseThrow(() -> new BusinessException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!owner.equals(userId)) {
            // 삭제된 타인 목표도 기존 active 조회 계약대로 404입니다.
            GoalAiPlanService.owned(goals.findActive(goalId), userId);
        }
    }

    private void recoverExpired(Long userId, Long goalId) {
        transaction.executeWithoutResult(status -> {
            lockOwner(userId, goalId);
            generations.findByGoalId(goalId).filter(g -> g.expired(LocalDateTime.now(clock)))
                    .ifPresent(g -> refundAndRelease(userId, g));
        });
    }

    private void releaseFailed(Long userId, Long goalId, String token) {
        transaction.executeWithoutResult(status -> {
            // 탈퇴가 끝났으면 사용자/예약을 복원하지 않습니다.
            if (users.findByIdForUpdate(userId).isEmpty()) {
                return;
            }
            if (generations.lockGoalOwner(goalId).filter(userId::equals).isEmpty()) {
                return;
            }
            generations.findByGoalId(goalId).filter(g -> g.owns(token)).ifPresent(g -> refundAndRelease(userId, g));
        });
    }

    private void refundAndRelease(Long userId, GoalReviewGeneration generation) {
        if (generation.isCharged()) {
            credits.giveBack(userId);
        }
        generation.release();
    }

    private String fingerprint(ReviewPayload payload) {
        // 오늘 날짜는 사용자 입력이 아닙니다. 자정이 지났다는 이유로 같은 입력에 다시 과금하지 않습니다.
        return hash(REVIEW_PROMPT + "\n" + json.write(payload));
    }

    private String resultFingerprint(GoalReview review) {
        return hash(json.write(new ResultSnapshot(review.getId(), review.isAchieved(), review.getComparison(),
                review.getMessage(), review.getCurrentStatus())));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record Creation(ReviewResponse response, boolean created) {
    }

    private record ResultSnapshot(Long id, boolean achieved, String comparison, String message, String currentStatus) {
    }

    public ReviewResponse get(Long userId, Long goalId) {
        return transaction.execute(status -> {
            Goal goal = GoalAiPlanService.owned(goals.findActive(goalId), userId);
            GoalReview review = reviews.findByGoalId(goalId)
                    .orElseThrow(() -> new BusinessException(GoalAiErrorCode.AI_REVIEW_NOT_FOUND));
            return ReviewResponse.from(goal, review);
        });
    }

    /**
     * 계획 중(PLANNING) → 종료일 미도달 → END 체크인 없음 순서로 막습니다. 종료 전이면 END 유무와 무관하게 먼저 걸립니다.
     * 직접 완료(COMPLETED)한 목표는 종료일 전이어도 끝난 것으로 봅니다.
     */
    private Goal requireReviewable(Goal goal, LocalDate today) {
        if (goal.getStatus() == GoalStatus.PLANNING) {
            throw new BusinessException(GoalAiErrorCode.AI_REVIEW_GOAL_NOT_STARTED);
        }
        if (!goal.isPast(today) && !goal.isCompleted()) {
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
        return new ReviewPayload(goal.getTitle(), goal.getCurrentStatus(), progressRate, snapshots, currentStatus);
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

    /** 프롬프트를 어겨도 화면에 한 줄로 나가도록 줄바꿈·연속 공백을 공백 하나로 바꾸고 MAX_LINE 글자(코드포인트)로 자릅니다. */
    static String oneLine(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.codePointCount(0, flat.length()) <= MAX_LINE) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, MAX_LINE)).strip();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReviewAnswer(Boolean achieved, String comparison, String message) {
    }

    record ReviewPayload(String title, String initialStatus, BigDecimal progressRate,
            List<CheckinSnapshot> checkins, String currentStatus) {
    }

    /** 읽기 트랜잭션에서 본 목표 데이터(payload)와 그 시점의 기존 리뷰 버전을 함께 들고 다닙니다. */
    record ReadSnapshot(ReviewPayload payload, ReviewVersion version, String inputHash, String token,
            ReviewResponse cached) {
    }

    /** 기존 리뷰의 식별자+수정시각. 쓰기 시점에 다시 읽은 값과 비교해 그 사이 다른 요청이 먼저 썼는지 판정합니다. */
    record ReviewVersion(Long id, LocalDateTime updatedAt) {
        static ReviewVersion of(Optional<GoalReview> review) {
            return review.map(r -> new ReviewVersion(r.getId(), r.getUpdatedAt())).orElse(null);
        }
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
