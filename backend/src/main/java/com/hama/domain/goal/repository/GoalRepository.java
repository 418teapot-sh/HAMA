package com.hama.domain.goal.repository;

import com.hama.domain.goal.entity.Goal;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    // 리스트에는 플랜이 적용된(저장 status = IN_PROGRESS) 목표만 나옵니다. PAST 는 종료일로 가릅니다.
    // 진행 중은 마감이 가까운 순, 지난 목표는 최근에 끝난 순, 전체는 최근에 만든 순입니다.

    @Query(value = """
            select g from Goal g
            where g.userId = :userId and g.deletedAt is null
              and g.status = com.hama.domain.goal.entity.GoalStatus.IN_PROGRESS and g.endDate >= :today
            order by g.endDate asc, g.id asc
            """,
            countQuery = """
            select count(g) from Goal g
            where g.userId = :userId and g.deletedAt is null
              and g.status = com.hama.domain.goal.entity.GoalStatus.IN_PROGRESS and g.endDate >= :today
            """)
    Page<Goal> findInProgress(@Param("userId") Long userId, @Param("today") LocalDate today, Pageable pageable);

    @Query(value = """
            select g from Goal g
            where g.userId = :userId and g.deletedAt is null
              and g.status = com.hama.domain.goal.entity.GoalStatus.IN_PROGRESS and g.endDate < :today
            order by g.endDate desc, g.id desc
            """,
            countQuery = """
            select count(g) from Goal g
            where g.userId = :userId and g.deletedAt is null
              and g.status = com.hama.domain.goal.entity.GoalStatus.IN_PROGRESS and g.endDate < :today
            """)
    Page<Goal> findPast(@Param("userId") Long userId, @Param("today") LocalDate today, Pageable pageable);

    @Query(value = """
            select g from Goal g
            where g.userId = :userId and g.deletedAt is null
              and g.status = com.hama.domain.goal.entity.GoalStatus.IN_PROGRESS
            order by g.id desc
            """,
            countQuery = """
            select count(g) from Goal g
            where g.userId = :userId and g.deletedAt is null
              and g.status = com.hama.domain.goal.entity.GoalStatus.IN_PROGRESS
            """)
    Page<Goal> findListed(@Param("userId") Long userId, Pageable pageable);

    @Query("select g from Goal g where g.id = :id and g.deletedAt is null")
    Optional<Goal> findActive(@Param("id") Long id);

    // 소유권은 조회 후 검사해야 타인 리소스 403 / 없는 리소스 404를 구분할 수 있습니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Goal g where g.id = :id and g.deletedAt is null")
    Optional<Goal> findActiveForUpdate(@Param("id") Long id);
}
