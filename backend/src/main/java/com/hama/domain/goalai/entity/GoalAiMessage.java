package com.hama.domain.goalai.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "goal_ai_message",
        indexes = @Index(name = "idx_goal_ai_message_session", columnList = "session_id,message_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalAiMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Expects expects;

    public static GoalAiMessage user(Long sessionId, String content) {
        return of(sessionId, MessageRole.USER, content, null);
    }

    public static GoalAiMessage ai(Long sessionId, String content, Expects expects) {
        return of(sessionId, MessageRole.AI, content, expects);
    }

    private static GoalAiMessage of(Long sessionId, MessageRole role, String content, Expects expects) {
        GoalAiMessage message = new GoalAiMessage();
        message.sessionId = sessionId;
        message.role = role;
        message.content = content;
        message.expects = expects;
        return message;
    }
}
