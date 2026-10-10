package com.hama.domain.goalai.service;

import static com.hama.domain.goalai.service.SessionGuards.owned;
import static com.hama.domain.goalai.service.SessionGuards.requireReady;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goalai.dto.DraftResponses;
import com.hama.domain.goalai.dto.GoalDraft;
import com.hama.domain.goalai.dto.RealityResult;
import com.hama.domain.goalai.dto.UpdateGoalDraftRequest;
import com.hama.domain.goalai.entity.GoalAiSession;
import com.hama.domain.goalai.repository.GoalAiSessionRepository;
import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiErrorCode;
import com.hama.global.ai.AiMessage;
import com.hama.global.ai.AiRequest;
import com.hama.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 초안이 준비된(READY) 세션의 현실성 체크·초안 수정·확정. */
@Service
public class GoalAiDraftService {

    private static final Set<String> VERDICTS = Set.of("FEASIBLE", "CHALLENGING", "UNREALISTIC");
    private static final int MAX_SUGGESTIONS = 3;
    private static final BigDecimal MAX_VALUE = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_WEEKLY_HOURS = new BigDecimal("168.0");

    private static final String REALITY_PROMPT = """
            너는 목표 달성 가능성을 냉정하게 평가하는 코치야. 오늘은 %s(KST)야.
            사용자 목표 초안(JSON)과 서버가 계산한 기간 내 총 가용시간을 보고, 목표 달성에 필요한 총 시간을 추정해 평가해.
            반드시 아래 필드를 가진 JSON 객체 하나로만 답해.
            {"verdict": FEASIBLE | CHALLENGING | UNREALISTIC,
             "comment": 2~3문장 한국어 평가,
             "requiredHours": 필요한 총 시간(정수),
             "needsSplit": 목표를 여러 개로 나눠야 할 만큼 크면 true,
             "suggestions": FEASIBLE 이면 [], 아니면 최대 3개 [{"type": EXTEND_PERIOD | LOWER_TARGET | MORE_TIME,
                "label": 한 줄 한국어 설명,
                "patch": EXTEND_PERIOD 는 {"endDate":"yyyy-MM-dd"}, LOWER_TARGET 는 {"targetValue":숫자},
                         MORE_TIME 는 {"weeklyAvailableHours":숫자}}]}
            """;

    private final GoalAiSessionRepository sessions;
    private final GoalRepository goals;
    private final AiClient aiClient;
    private final GoalAiJson json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public GoalAiDraftService(GoalAiSessionRepository sessions, GoalRepository goals, AiClient aiClient,
            GoalAiJson json, TransactionTemplate transaction, @Qualifier("be3Clock") Clock clock) {
        this.sessions = sessions;
        this.goals = goals;
        this.aiClient = aiClient;
        this.json = json;
        this.transaction = transaction;
        this.clock = clock;
    }

    public RealityResult realityCheck(Long userId, Long sessionId) {
        String draftJson = transaction.execute(status -> {
            GoalAiSession session = owned(sessions.findById(sessionId), userId);
            requireReady(session);
            return session.getDraftJson();
        });
        GoalDraft draft = json.read(draftJson, GoalDraft.class);
        LocalDate today = LocalDate.now(clock);
        Integer available = availableHours(draft);

        RealityAnswer answer = aiClient.chatForJson(AiRequest.of("reality-check", REALITY_PROMPT.formatted(today),
                List.of(AiMessage.user("초안: " + draftJson + "\n기간 내 총 가용시간: "
                        + (available == null ? "알 수 없음" : available + "시간")))), RealityAnswer.class);
        RealityResult result = toResult(answer, draft, available);

        transaction.executeWithoutResult(status -> {
            GoalAiSession session = owned(sessions.findForUpdate(sessionId), userId);
            requireReady(session);
            // AI 를 기다리는 사이 초안이 바뀌었으면 이 결과는 옛 초안 기준이라 저장하지 않습니다.
            if (Objects.equals(session.getDraftJson(), draftJson)) {
                session.recordReality(json.write(result));
            }
        });
        return result;
    }

    public DraftResponses.DraftView updateDraft(Long userId, Long sessionId, UpdateGoalDraftRequest request) {
        LocalDate today = LocalDate.now(clock);
        return transaction.execute(status -> {
            GoalAiSession session = owned(sessions.findForUpdate(sessionId), userId);
            requireReady(session);
            GoalDraft merged = request.mergeInto(json.read(session.getDraftJson(), GoalDraft.class));
            Goal.validateContent(merged.toContent(), today);
            session.updateDraft(json.write(merged));
            return new DraftResponses.DraftView(merged);
        });
    }

    public DraftResponses.Confirmed confirm(Long userId, Long sessionId) {
        LocalDate today = LocalDate.now(clock);
        return transaction.execute(status -> {
            GoalAiSession session = owned(sessions.findForUpdate(sessionId), userId);
            requireReady(session);
            GoalDraft draft = json.read(session.getDraftJson(), GoalDraft.class);
            RealityResult reality = json.read(session.getRealityJson(), RealityResult.class);
            Goal goal = goals.save(Goal.createPlanning(userId, draft.toContent(), draft.currentLevel(),
                    reality == null ? null : reality.verdict(), reality == null ? null : reality.comment(), today));
            session.confirm(goal.getId());
            return new DraftResponses.Confirmed(goal.getId(), goal.getStatus());
        });
    }

    /** 기간(시작·종료일 포함) 동안의 총 가용시간. 주간 가용시간이 없으면 null. */
    static Integer availableHours(GoalDraft draft) {
        if (draft.weeklyAvailableHours() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(draft.startDate(), draft.endDate()) + 1;
        return draft.weeklyAvailableHours().multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(7), 0, RoundingMode.HALF_UP).intValue();
    }

    static RealityResult toResult(RealityAnswer answer, GoalDraft draft, Integer available) {
        String verdict = answer == null || answer.verdict() == null
                ? null : answer.verdict().strip().toUpperCase(Locale.ROOT);
        if (verdict == null || !VERDICTS.contains(verdict)
                || answer.comment() == null || answer.comment().isBlank()) {
            throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR,
                    new IllegalStateException("[reality-check] AI 응답에 verdict/comment 가 없습니다."));
        }
        Integer required = answer.requiredHours() == null ? null : Math.max(0, answer.requiredHours());
        Integer gap = required == null || available == null ? null : Math.max(0, required - available);
        return new RealityResult(verdict, cut(answer.comment(), 2000),
                new RealityResult.Comparison(required, available, gap),
                Boolean.TRUE.equals(answer.needsSplit()),
                suggestions(answer.suggestions(), draft));
    }

    /** 초안에 바로 적용할 수 있는 제안만 남깁니다. 타입별로 하나, 허용된 patch 키만 받습니다. */
    static List<RealityResult.Suggestion> suggestions(List<SuggestionAnswer> raw, GoalDraft draft) {
        List<RealityResult.Suggestion> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (SuggestionAnswer suggestion : raw) {
            if (result.size() == MAX_SUGGESTIONS) {
                break;
            }
            if (suggestion == null || suggestion.type() == null || suggestion.label() == null
                    || suggestion.label().isBlank() || suggestion.patch() == null) {
                continue;
            }
            String type = suggestion.type().strip().toUpperCase(Locale.ROOT);
            Map<String, Object> patch = switch (type) {
                case "EXTEND_PERIOD" -> extendPeriod(suggestion.patch().get("endDate"), draft);
                case "LOWER_TARGET" -> lowerTarget(suggestion.patch().get("targetValue"), draft);
                case "MORE_TIME" -> moreTime(suggestion.patch().get("weeklyAvailableHours"), draft);
                default -> null;
            };
            if (patch != null && seen.add(type)) {
                result.add(new RealityResult.Suggestion(type, cut(suggestion.label(), 200), patch));
            }
        }
        return result;
    }

    private static Map<String, Object> extendPeriod(Object value, GoalDraft draft) {
        try {
            LocalDate end = LocalDate.parse(String.valueOf(value));
            return end.isAfter(draft.endDate()) && end.getYear() <= 9999
                    && Goal.withinMaxPeriod(draft.startDate(), end) ? Map.of("endDate", end.toString()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, Object> lowerTarget(Object value, GoalDraft draft) {
        BigDecimal target = decimal(value, 2);
        if (target == null || target.abs().compareTo(MAX_VALUE) > 0
                || (draft.targetValue() != null && target.compareTo(draft.targetValue()) == 0)) {
            return null;
        }
        return Map.of("targetValue", target);
    }

    private static Map<String, Object> moreTime(Object value, GoalDraft draft) {
        BigDecimal hours = decimal(value, 1);
        if (hours == null || hours.signum() <= 0 || hours.compareTo(MAX_WEEKLY_HOURS) > 0
                || (draft.weeklyAvailableHours() != null && hours.compareTo(draft.weeklyAvailableHours()) <= 0)) {
            return null;
        }
        return Map.of("weeklyAvailableHours", hours);
    }

    private static BigDecimal decimal(Object value, int scale) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).strip()).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cut(String text, int max) {
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RealityAnswer(String verdict, String comment, Integer requiredHours, Boolean needsSplit,
            List<SuggestionAnswer> suggestions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SuggestionAnswer(String type, String label, Map<String, Object> patch) {
    }
}
