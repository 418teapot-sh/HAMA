package com.hama.domain.goalai.repository;

import com.hama.domain.goalai.entity.GoalPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalPlanRepository extends JpaRepository<GoalPlan, Long> {

    List<GoalPlan> findByGoalIdOrderByIdAsc(Long goalId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GoalPlan p where p.goalId = :goalId and p.selected = false")
    int deleteUnselected(@Param("goalId") Long goalId);
}
