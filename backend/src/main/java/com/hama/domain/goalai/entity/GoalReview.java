package com.hama.domain.goalai.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 목표 종료 AI 리뷰. 목표당 1건(goal_id unique)이고, 다시 만들면 기존 값을 덮어씁니다. */
@Entity
@Table(name = "goal_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(nullable = false)
    private boolean achieved;

    @Column(nullable = false, length = 500)
    private String comparison;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "current_status", length = 200)
    private String currentStatus;

    public static GoalReview create(Long goalId, boolean achieved, String comparison, String message,
            String currentStatus) {
        GoalReview review = new GoalReview();
        review.goalId = goalId;
        review.overwrite(achieved, comparison, message, currentStatus);
        return review;
    }

    /** 같은 목표를 다시 리뷰하면 이 메서드로 기존 행을 덮어씁니다. */
    public void overwrite(boolean achieved, String comparison, String message, String currentStatus) {
        this.achieved = achieved;
        this.comparison = comparison;
        this.message = message;
        this.currentStatus = currentStatus;
    }
}
