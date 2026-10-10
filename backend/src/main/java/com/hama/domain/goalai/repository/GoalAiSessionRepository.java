package com.hama.domain.goalai.repository;

import com.hama.domain.goalai.entity.GoalAiSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalAiSessionRepository extends JpaRepository<GoalAiSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GoalAiSession s where s.id = :id")
    Optional<GoalAiSession> findForUpdate(@Param("id") Long id);
}
