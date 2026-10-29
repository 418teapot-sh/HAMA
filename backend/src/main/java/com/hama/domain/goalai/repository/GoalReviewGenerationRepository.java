package com.hama.domain.goalai.repository;

import com.hama.domain.goalai.entity.GoalReviewGeneration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GoalReviewGenerationRepository extends JpaRepository<GoalReviewGeneration, Long> {

    Optional<GoalReviewGeneration> findByGoalId(Long goalId);

    /** 소유권 선검사는 삭제된 자기 목표의 환급도 허용해야 하므로 active 조회와 구분합니다. */
    @Query(value = "SELECT user_id AS userId, deleted_at AS deletedAt FROM goal WHERE goal_id = :goalId", nativeQuery = true)
    Optional<GoalOwner> findGoalOwner(Long goalId);

    interface GoalOwner {
        Long getUserId();
        LocalDateTime getDeletedAt();
    }

    /** 삭제된 목표도 잠가야 AI 대기 중 소프트 삭제된 예약의 차감분을 돌려줄 수 있습니다. */
    @Query(value = "SELECT user_id FROM goal WHERE goal_id = :goalId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockGoalOwner(Long goalId);
}
