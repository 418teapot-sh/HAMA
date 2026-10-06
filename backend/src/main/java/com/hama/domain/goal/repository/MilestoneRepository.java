package com.hama.domain.goal.repository;

import com.hama.domain.goal.entity.Milestone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByGoalIdOrderBySeqAsc(Long goalId);
}
