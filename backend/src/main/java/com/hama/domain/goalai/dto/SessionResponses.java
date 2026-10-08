package com.hama.domain.goalai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.goalai.entity.Expects;
import com.hama.domain.goalai.entity.GoalAiMessage;
import com.hama.domain.goalai.entity.MessageRole;
import com.hama.domain.goalai.entity.SessionStatus;
import java.time.LocalDateTime;
import java.util.List;

public final class SessionResponses {

    private SessionResponses() {
    }

    public record AiMessageView(Long messageId, String content, Expects expects) {

        public static AiMessageView from(GoalAiMessage message) {
            return new AiMessageView(message.getId(), message.getContent(), message.getExpects());
        }
    }

    public record Started(Long sessionId, SessionStatus status, AiMessageView aiMessage) {
    }

    /** 수집 중이면 goalDraft 는 null 입니다. */
    public record Reply(SessionStatus status, AiMessageView aiMessage, GoalDraft goalDraft) {
    }

    public record MessageView(Long messageId, MessageRole role, String content,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt) {

        public static MessageView from(GoalAiMessage message) {
            return new MessageView(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
        }
    }

    public record Detail(Long sessionId, SessionStatus status, String rawGoal, Long goalId,
            List<MessageView> messages, GoalDraft goalDraft, RealityResult realityResult) {
    }
}
