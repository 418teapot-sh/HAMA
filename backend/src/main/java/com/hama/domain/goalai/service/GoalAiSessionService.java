package com.hama.domain.goalai.service;

import static com.hama.domain.goalai.service.SessionGuards.owned;
import static com.hama.domain.goalai.service.SessionGuards.requireOpen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.goalai.dto.GoalDraft;
import com.hama.domain.goalai.dto.RealityResult;
import com.hama.domain.goalai.dto.SessionResponses;
import com.hama.domain.goalai.entity.Expects;
import com.hama.domain.goalai.entity.GoalAiMessage;
import com.hama.domain.goalai.entity.GoalAiSession;
import com.hama.domain.goalai.entity.MessageRole;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.goalai.repository.GoalAiMessageRepository;
import com.hama.domain.goalai.repository.GoalAiSessionRepository;
import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiErrorCode;
import com.hama.global.ai.AiMessage;
import com.hama.global.ai.AiRequest;
import com.hama.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 목표 대화 세션. AI 호출(최대 수십 초) 동안 DB 커넥션을 잡지 않도록 읽기 → AI 호출 → 쓰기를 트랜잭션 두 개로 나눕니다.
 */
@Service
public class GoalAiSessionService {

    /** rawGoal 을 포함한 사용자 메시지 수 상한. 대화가 끝없이 이어지며 토큰을 쓰는 것을 막습니다. */
    static final int MAX_USER_MESSAGES = 10;

    private static final String SESSION_PROMPT = """
            너는 사용자의 막연한 목표를 측정 가능한 목표로 구체화하는 코치야. 오늘은 %s(KST)야.
            현재 수준, 목표 기간(시작일·종료일), 주간 가용시간을 알아야 초안을 만들 수 있어. 모르는 것만 한 번에 하나씩 짧게 물어봐.
            반드시 아래 필드를 가진 JSON 객체 하나로만 답해.
            {"reply": 사용자에게 보낼 한국어 문장,
             "expects": 다음 답변으로 기대하는 정보 CURRENT_LEVEL | PERIOD | AVAILABLE_TIME | OTHER (다 모였으면 null),
             "ready": 초안을 만들 정보가 다 모였으면 true 아니면 false,
             "draft": ready 일 때만 {"title": 100자 이하 목표 문장, "metricName": 측정 지표, "unit": 단위,
                      "startValue": 현재 수치, "targetValue": 목표 수치, "startDate": "yyyy-MM-dd", "endDate": "yyyy-MM-dd",
                      "weeklyAvailableHours": 주간 가용시간 숫자, "currentLevel": 현재 수준 설명}, 아니면 null}
            시작일을 말하지 않으면 오늘로 하고, 종료일은 오늘 이후여야 해. 기간은 최대 1년이야.
            """;

    private final GoalAiSessionRepository sessions;
    private final GoalAiMessageRepository messages;
    private final AiClient aiClient;
    private final GoalAiJson json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public GoalAiSessionService(GoalAiSessionRepository sessions, GoalAiMessageRepository messages,
            AiClient aiClient, GoalAiJson json, TransactionTemplate transaction,
            @Qualifier("be3Clock") Clock clock) {
        this.sessions = sessions;
        this.messages = messages;
        this.aiClient = aiClient;
        this.json = json;
        this.transaction = transaction;
        this.clock = clock;
    }

    public SessionResponses.Started start(Long userId, String rawGoal) {
        Turn turn = ask(List.of(AiMessage.user(rawGoal)));
        return transaction.execute(status -> {
            GoalAiSession session = sessions.save(GoalAiSession.start(userId, rawGoal));
            messages.save(GoalAiMessage.user(session.getId(), rawGoal));
            GoalAiMessage reply = messages.save(GoalAiMessage.ai(session.getId(), turn.reply(), turn.expects()));
            session.updateDraft(json.write(turn.draft()));
            return new SessionResponses.Started(session.getId(), session.getStatus(),
                    SessionResponses.AiMessageView.from(reply));
        });
    }

    public SessionResponses.Reply reply(Long userId, Long sessionId, String content) {
        List<AiMessage> history = transaction.execute(status -> {
            GoalAiSession session = owned(sessions.findById(sessionId), userId);
            requireOpen(session);
            if (messages.countBySessionIdAndRole(sessionId, MessageRole.USER) >= MAX_USER_MESSAGES) {
                throw new BusinessException(GoalAiErrorCode.AI_SESSION_TURN_LIMIT);
            }
            List<AiMessage> previous = new ArrayList<>();
            for (GoalAiMessage message : messages.findBySessionIdOrderByIdAsc(sessionId)) {
                previous.add(message.getRole() == MessageRole.USER
                        ? AiMessage.user(message.getContent()) : AiMessage.assistant(message.getContent()));
            }
            previous.add(AiMessage.user(content));
            return previous;
        });
        Turn turn = ask(history);
        return transaction.execute(status -> {
            GoalAiSession session = owned(sessions.findForUpdate(sessionId), userId);
            requireOpen(session);
            messages.save(GoalAiMessage.user(sessionId, content));
            GoalAiMessage reply = messages.save(GoalAiMessage.ai(sessionId, turn.reply(), turn.expects()));
            session.updateDraft(json.write(turn.draft()));
            return new SessionResponses.Reply(session.getStatus(), SessionResponses.AiMessageView.from(reply),
                    turn.draft());
        });
    }

    public SessionResponses.Detail get(Long userId, Long sessionId) {
        return transaction.execute(status -> {
            GoalAiSession session = owned(sessions.findById(sessionId), userId);
            return new SessionResponses.Detail(session.getId(), session.getStatus(), session.getRawGoal(),
                    session.getGoalId(),
                    messages.findBySessionIdOrderByIdAsc(sessionId).stream()
                            .map(SessionResponses.MessageView::from).toList(),
                    json.read(session.getDraftJson(), GoalDraft.class),
                    json.read(session.getRealityJson(), RealityResult.class));
        });
    }

    private Turn ask(List<AiMessage> history) {
        LocalDate today = LocalDate.now(clock);
        SessionTurn answer = aiClient.chatForJson(
                AiRequest.of("goal-session", SESSION_PROMPT.formatted(today), history), SessionTurn.class);
        if (answer == null || answer.reply() == null || answer.reply().isBlank()) {
            throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR,
                    new IllegalStateException("[goal-session] AI 응답에 reply 가 없습니다."));
        }
        GoalDraft draft = Boolean.TRUE.equals(answer.ready()) && answer.draft() != null
                ? answer.draft().normalized(today) : null;
        return new Turn(answer.reply().strip(), draft == null ? Expects.parse(answer.expects()) : null, draft);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SessionTurn(String reply, String expects, Boolean ready, GoalDraft draft) {
    }

    private record Turn(String reply, Expects expects, GoalDraft draft) {
    }
}
