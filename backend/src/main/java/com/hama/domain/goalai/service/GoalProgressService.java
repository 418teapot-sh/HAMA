package com.hama.domain.goalai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.domain.goalai.dto.GoalProgressResponses;
import com.hama.domain.goalai.entity.GoalProgressSummary;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.domain.goalai.repository.GoalProgressSummaryRepository;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.ai.AiClient;
import com.hama.global.ai.AiErrorCode;
import com.hama.global.ai.AiMessage;
import com.hama.global.ai.AiRequest;
import com.hama.global.ai.LinerProperties;
import com.hama.global.exception.BusinessException;
import com.hama.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class GoalProgressService {

    private final GoalRepository goals;
    private final TodoRepository todos;
    private final GoalProgressSummaryRepository summaries;
    private final GoalProgressSource source;
    private final AiClient ai;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final long leaseSeconds;

    public GoalProgressService(GoalRepository goals, TodoRepository todos, GoalProgressSummaryRepository summaries,
            GoalProgressSource source, AiClient ai, @Qualifier("be3Clock") Clock clock,
            TransactionTemplate transaction, LinerProperties liner) {
        this.goals = goals;
        this.todos = todos;
        this.summaries = summaries;
        this.source = source;
        this.ai = ai;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transaction.getTransactionManager());
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.leaseSeconds = Math.max(120L, (long) liner.timeoutSeconds() + 60L);
    }

    public PageResponse<GoalProgressResponses.Record> records(Long userId, Long goalId, Pageable pageable) {
        return transaction.execute(tx -> {
            GoalAiPlanService.owned(goals.findActive(goalId), userId);
            return PageResponse.from(todos.findProgressRecords(userId, goalId, pageable),
                    GoalProgressResponses.Record::from);
        });
    }

    public GoalProgressResponses.Summary get(Long userId, Long goalId) {
        return inspect(userId, goalId, false);
    }

    public GoalProgressResponses.Summary retry(Long userId, Long goalId) {
        return inspect(userId, goalId, true);
    }

    private GoalProgressResponses.Summary inspect(Long userId, Long goalId, boolean retry) {
        return transaction.execute(tx -> {
            Goal goal = GoalAiPlanService.owned(goals.findActiveForUpdate(goalId), userId);
            if (retry && !active(goal)) throw new BusinessException(GoalAiErrorCode.GOAL_NOT_IN_PROGRESS);
            GoalProgressSummary state = summaries.findForUpdate(goalId)
                    .orElseGet(() -> summaries.save(GoalProgressSummary.create(goalId, now())));
            GoalProgressSource.Input input = source.read(goal);
            boolean same = input.hash().equals(state.getSourceHash());
            if (!active(goal)) {
                state.inactive();
            } else if (same) {
                state.reuse();
            } else if (input.used() == 0) {
                state.complete(input.hash(), null, 0, input.total(), now());
            } else if (state.getRevision() == state.getProcessedRevision()
                    || (retry && !state.running(now()) && state.getStatus().equals("FAILED"))) {
                state.markDirty(now());
            }
            boolean stale = state.getSummary() != null && !same;
            return new GoalProgressResponses.Summary(goalId, state.getStatus(), state.getSummary(),
                    state.getGeneratedAt(), stale, state.getUsedRecordCount(), state.getTotalRecordCount(),
                    input.total(), state.getAttempts(), state.getStatus().equals("PENDING")
                            ? state.getNextAttemptAt() : null);
        });
    }

    /** 후보는 잠금 없이 읽고 실제 예약은 목표 → 요약 순서입니다. 다중 인스턴스도 토큰으로 구분합니다. */
    public void processDue() {
        var ids = transaction.execute(tx -> summaries.findDue(now(), PageRequest.of(0, 5)));
        for (Long goalId : ids) {
            try {
                process(goalId);
            } catch (RuntimeException failure) {
                log.warn("[goal-progress] claim failed goalId={} error={}", goalId,
                        failure.getClass().getSimpleName());
            }
        }
    }

    public void process(Long goalId) {
        Job job = transaction.execute(tx -> claim(goalId));
        if (job == null) return;
        try {
            Answer answer = ai.chatForJson(AiRequest.of("goal-progress-summary", GoalProgressSource.PROMPT,
                    java.util.List.of(AiMessage.user(job.input().json()))), Answer.class);
            if (answer == null || answer.summary() == null || answer.summary().isBlank()) {
                throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR,
                        new IllegalStateException("[goal-progress] summary is missing"));
            }
            String text = GoalProgressSource.limit(answer.summary().strip(), 1000);
            transaction.executeWithoutResult(tx -> {
                var goal = goals.findActiveForUpdate(goalId);
                var state = summaries.findForUpdate(goalId);
                if (state.isEmpty() || !state.get().owns(job.token())) return;
                if (goal.isEmpty() || !active(goal.get())) {
                    state.get().inactive();
                    return;
                }
                var current = source.read(goal.get());
                if (state.get().getRevision() != job.revision() || !current.hash().equals(job.input().hash())
                        || !state.get().running(now())) {
                    state.get().superseded(now());
                    return;
                }
                state.get().complete(current.hash(), text, current.used(), current.total(), now());
            });
        } catch (RuntimeException failure) {
            // 사용자 글/프롬프트/AI 응답을 로그에 남기지 않습니다.
            log.warn("[goal-progress] generation failed goalId={} error={}", goalId,
                    failure.getClass().getSimpleName());
            try {
                transaction.executeWithoutResult(tx -> {
                    var goal = goals.findActiveForUpdate(goalId);
                    summaries.findForUpdate(goalId).filter(s -> s.owns(job.token())).ifPresent(state -> {
                        if (goal.isEmpty() || !active(goal.get())) state.inactive();
                        else if (state.getRevision() != job.revision()) state.superseded(now());
                        else state.failed(now());
                    });
                });
            } catch (RuntimeException persistenceFailure) {
                // 예약이 DB에 남아 만료 후 회수됩니다. 한 후보 오류가 다른 목표 작업을 막지 않습니다.
                log.warn("[goal-progress] failure state save failed goalId={} error={}", goalId,
                        persistenceFailure.getClass().getSimpleName());
            }
        }
    }

    private Job claim(Long goalId) {
        var goal = goals.findActiveForUpdate(goalId);
        var found = summaries.findForUpdate(goalId);
        if (found.isEmpty()) return null;
        GoalProgressSummary state = found.get();
        if (goal.isEmpty() || !active(goal.get())) {
            state.inactive();
            return null;
        }
        if (state.running(now())) return null;
        if (state.getAttempts() >= 3) {
            if (state.getRequestToken() != null) state.failed(now());
            return null;
        }
        if (!state.canClaim(now())) return null;
        var input = source.read(goal.get());
        if (input.hash().equals(state.getSourceHash())) {
            state.reuse();
            return null;
        }
        if (input.used() == 0) {
            state.complete(input.hash(), null, 0, input.total(), now());
            return null;
        }
        String token = UUID.randomUUID().toString();
        state.claim(token, now(), leaseSeconds);
        return new Job(goalId, state.getRevision(), token, input);
    }

    private boolean active(Goal goal) {
        return goal.effectiveStatus(LocalDate.now(clock)) == GoalStatus.IN_PROGRESS;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record Job(Long goalId, long revision, String token, GoalProgressSource.Input input) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Answer(String summary) {
    }
}
