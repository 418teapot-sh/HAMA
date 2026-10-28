package com.hama.domain.goalai.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원본 변경과 같은 트랜잭션에서 dirty를 남깁니다. 항상 목표 → 요약 순서로 잠급니다. */
@Entity
@Table(name = "goal_progress_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalProgressSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long id;
    @Column(name = "goal_id", nullable = false)
    private Long goalId;
    @Column(nullable = false)
    private long revision;
    @Column(name = "processed_revision", nullable = false)
    private long processedRevision;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(name = "request_token", length = 36)
    private String requestToken;
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;
    @Column(name = "source_hash", length = 64)
    private String sourceHash;
    @Column(length = 1000)
    private String summary;
    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
    @Column(name = "used_record_count", nullable = false)
    private int usedRecordCount;
    @Column(name = "total_record_count", nullable = false)
    private long totalRecordCount;

    public static GoalProgressSummary create(Long goalId, LocalDateTime now) {
        GoalProgressSummary state = new GoalProgressSummary();
        state.goalId = goalId;
        state.markDirty(now);
        return state;
    }

    public void markDirty(LocalDateTime now) {
        revision++;
        attempts = 0;
        nextAttemptAt = now;
        status = requestToken == null ? "PENDING" : "GENERATING";
    }

    public boolean running(LocalDateTime now) {
        return requestToken != null && leaseUntil.isAfter(now);
    }

    public boolean canClaim(LocalDateTime now) {
        return revision > processedRevision && attempts < 3 && !nextAttemptAt.isAfter(now) && !running(now);
    }

    public void claim(String token, LocalDateTime now, long leaseSeconds) {
        requestToken = token;
        leaseUntil = now.plusSeconds(leaseSeconds);
        attempts++;
        status = "GENERATING";
    }

    public boolean owns(String token) {
        return token != null && token.equals(requestToken);
    }

    public void complete(String hash, String text, int used, long total, LocalDateTime now) {
        sourceHash = hash;
        summary = text;
        usedRecordCount = used;
        totalRecordCount = total;
        generatedAt = text == null ? null : now;
        processedRevision = revision;
        attempts = 0;
        status = text == null ? "EMPTY" : "READY";
        release();
    }

    public void reuse() {
        processedRevision = revision;
        attempts = 0;
        status = summary == null ? "EMPTY" : "READY";
        release();
    }

    public void superseded(LocalDateTime now) {
        release();
        attempts = 0;
        nextAttemptAt = now;
        status = "PENDING";
    }

    public void failed(LocalDateTime now) {
        release();
        status = attempts >= 3 ? "FAILED" : "PENDING";
        nextAttemptAt = now.plusSeconds(30L * attempts);
    }

    public void inactive() {
        processedRevision = revision;
        status = "INACTIVE";
        release();
    }

    private void release() {
        requestToken = null;
        leaseUntil = null;
    }
}
