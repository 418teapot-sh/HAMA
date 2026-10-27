package com.hama.domain.goalai.repository;

import com.hama.domain.goalai.entity.GoalPlan;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalPlanRepository extends JpaRepository<GoalPlan, Long> {

    List<GoalPlan> findByGoalIdOrderByIdAsc(Long goalId);

    boolean existsByGoalIdAndSelectedTrue(Long goalId);

    /** 목표 리스트의 aiPlanApplied 라벨용. 선택된 플랜을 가진 목표 id만 돌려줍니다(없으면 빈 Set). */
    @Query("select p.goalId from GoalPlan p where p.selected = true and p.goalId in :goalIds")
    Set<Long> findGoalIdsBySelectedTrueAndGoalIdIn(@Param("goalIds") Collection<Long> goalIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from GoalPlan p where p.goalId = :goalId and p.selected = false")
    int deleteUnselected(@Param("goalId") Long goalId);
}
