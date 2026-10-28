package com.hama.domain.goal.repository;

import com.hama.domain.goal.entity.GoalFixedSchedule;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalFixedScheduleRepository extends JpaRepository<GoalFixedSchedule, Long> {

    List<GoalFixedSchedule> findByGoalIdOrderByDayOfWeekAsc(Long goalId);

    List<GoalFixedSchedule> findByGoalIdIn(Collection<Long> goalIds);
}
