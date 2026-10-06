package com.hama.domain.goal.repository;

import com.hama.domain.goal.entity.PeriodGoal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodGoalRepository extends JpaRepository<PeriodGoal, Long> {

    List<PeriodGoal> findByGoalIdOrderByMilestoneIdAscSeqAsc(Long goalId);
}
