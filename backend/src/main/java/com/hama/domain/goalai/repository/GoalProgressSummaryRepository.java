package com.hama.domain.goalai.repository;

import com.hama.domain.goalai.entity.GoalProgressSummary;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GoalProgressSummaryRepository extends JpaRepository<GoalProgressSummary, Long> {

    Optional<GoalProgressSummary> findByGoalId(Long goalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GoalProgressSummary s where s.goalId = :goalId")
    Optional<GoalProgressSummary> findForUpdate(Long goalId);

    @Query("""
            select s.goalId from GoalProgressSummary s
            where s.revision > s.processedRevision and (s.attempts < 3 or s.requestToken is not null) and s.nextAttemptAt <= :now
              and (s.requestToken is null or s.leaseUntil <= :now)
            order by s.nextAttemptAt, s.id
            """)
    List<Long> findDue(LocalDateTime now, Pageable pageable);
}
