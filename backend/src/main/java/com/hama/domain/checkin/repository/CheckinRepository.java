package com.hama.domain.checkin.repository;

import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckinRepository extends JpaRepository<GoalCheckin, Long> {
    // 부모 잠금 전에 엔티티를 읽지 않아 대기 중 변경된 체크인을 캐시하지 않습니다.
    @Query("select c.goalId from GoalCheckin c where c.id = :id")
    Optional<Long> findGoalId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GoalCheckin c where c.id = :id and c.goalId = :goalId")
    Optional<GoalCheckin> findForUpdate(@Param("id") Long id, @Param("goalId") Long goalId);

    boolean existsByGoalIdAndType(Long goalId, CheckinType type);
    Page<GoalCheckin> findByGoalIdOrderByCheckedAtDescIdDesc(Long goalId, Pageable pageable);
}
