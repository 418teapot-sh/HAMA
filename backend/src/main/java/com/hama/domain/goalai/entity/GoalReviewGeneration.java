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

/** 목표 잠금 아래에서만 변경합니다. 실패한 새 입력은 이전 성공 결과의 지문을 지우지 않습니다. */
@Entity
@Table(name = "goal_review_generation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalReviewGeneration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generation_id")
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "request_token", length = 36)
    private String requestToken;

    @Column(name = "request_input_hash", length = 64)
    private String requestInputHash;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(nullable = false)
    private boolean charged;

    @Column(name = "successful_input_hash", length = 64)
    private String successfulInputHash;

    @Column(name = "successful_result_hash", length = 64)
    private String successfulResultHash;

    public static GoalReviewGeneration create(Long goalId) {
        GoalReviewGeneration generation = new GoalReviewGeneration();
        generation.goalId = goalId;
        return generation;
    }

    public boolean reserved() {
        return requestToken != null;
    }

    public boolean expired(LocalDateTime now) {
        return reserved() && !leaseUntil.isAfter(now);
    }

    public boolean owns(String token) {
        return token != null && token.equals(requestToken);
    }

    public void reserve(String token, String inputHash, LocalDateTime until) {
        requestToken = token;
        requestInputHash = inputHash;
        leaseUntil = until;
        charged = true;
    }

    public void complete(String inputHash, String resultHash) {
        successfulInputHash = inputHash;
        successfulResultHash = resultHash;
        release();
    }

    /** 실제 환급과 같은 트랜잭션에서 호출하거나, 정상 결과 저장 후 호출합니다. */
    public void release() {
        requestToken = null;
        requestInputHash = null;
        leaseUntil = null;
        charged = false;
    }
}
