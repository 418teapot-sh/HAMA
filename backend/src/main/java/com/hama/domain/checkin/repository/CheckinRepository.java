package com.hama.domain.checkin.repository;

import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRepository extends JpaRepository<GoalCheckin, Long> {
    boolean existsByGoalIdAndType(Long goalId, CheckinType type);
    Page<GoalCheckin> findByGoalIdOrderByCheckedAtDescIdDesc(Long goalId, Pageable pageable);
}
