package com.hama.domain.goalai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goalai.entity.GoalReview;
import java.time.LocalDateTime;

/** 목표 종료 AI 리뷰 응답. resultStatus 는 goal.result_status(ACHIEVED/MISSED)와 같습니다. */
public record ReviewResponse(Long goalId, Boolean achieved, String comparison, String message, String currentStatus,
        String resultStatus, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt) {

    public static ReviewResponse from(Goal goal, GoalReview review) {
        return new ReviewResponse(goal.getId(), review.isAchieved(), review.getComparison(), review.getMessage(),
                review.getCurrentStatus(), goal.getResultStatus(), review.getCreatedAt());
    }
}
