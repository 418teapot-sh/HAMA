package com.hama.domain.schedule.repository;

import com.hama.domain.schedule.entity.Schedule;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndDeletedAtIsNull(Long id);

    // 병합 전에 잠가 동시 PATCH의 필드 유실과 DELETE 후 되살아나는 경쟁을 방지합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.id = :id and s.deletedAt is null")
    Optional<Schedule> findActiveForUpdate(@Param("id") Long id);
}
