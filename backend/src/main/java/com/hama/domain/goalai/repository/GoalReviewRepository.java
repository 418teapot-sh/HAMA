package com.hama.domain.goalai.repository;

import com.hama.domain.goalai.entity.GoalReview;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalReviewRepository extends JpaRepository<GoalReview, Long> {

    Optional<GoalReview> findByGoalId(Long goalId);
}
