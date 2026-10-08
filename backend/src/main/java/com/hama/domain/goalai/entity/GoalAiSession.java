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

/** 초안·현실성 결과는 JSON 문자열로 들고 있고, 변환은 서비스의 GoalAiJson 이 합니다. */
@Entity
@Table(name = "goal_ai_session", indexes = @Index(name = "idx_goal_ai_session_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalAiSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "raw_goal", nullable = false, columnDefinition = "TEXT")
    private String rawGoal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "draft_json", columnDefinition = "TEXT")
    private String draftJson;

    @Column(name = "reality_json", columnDefinition = "TEXT")
    private String realityJson;

    @Column(name = "goal_id")
    private Long goalId;

    public static GoalAiSession start(Long userId, String rawGoal) {
        GoalAiSession session = new GoalAiSession();
        session.userId = userId;
        session.rawGoal = rawGoal;
        session.status = SessionStatus.COLLECTING;
        return session;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** AI 가 정리한 초안을 받습니다. null 이면 아직 수집 중입니다. 초안이 바뀌면 이전 현실성 결과는 버립니다. */
    public void updateDraft(String draftJson) {
        this.status = draftJson == null ? SessionStatus.COLLECTING : SessionStatus.READY;
        this.draftJson = draftJson;
        this.realityJson = null;
    }

    public void recordReality(String realityJson) {
        this.realityJson = realityJson;
    }

    public void confirm(Long goalId) {
        this.status = SessionStatus.CONFIRMED;
        this.goalId = goalId;
    }
}
