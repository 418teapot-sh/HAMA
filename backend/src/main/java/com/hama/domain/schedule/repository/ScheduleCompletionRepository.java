package com.hama.domain.schedule.repository;

import com.hama.domain.schedule.entity.ScheduleCompletion;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleCompletionRepository extends JpaRepository<ScheduleCompletion, Long> {
    Optional<ScheduleCompletion> findByScheduleIdAndOccurrenceDateAndDisplayDate(
            Long scheduleId, LocalDate occurrenceDate, LocalDate displayDate);
    List<ScheduleCompletion> findByScheduleId(Long scheduleId);

    @Modifying
    @Query("delete from ScheduleCompletion c where c.scheduleId = :scheduleId")
    void deleteAllForSchedule(@Param("scheduleId") Long scheduleId);

    @Query("select c from ScheduleCompletion c where c.userId = :userId and c.scheduleId in :ids "
            + "and c.displayDate between :from and :to")
    List<ScheduleCompletion> findForCalendar(@Param("userId") Long userId, @Param("ids") Collection<Long> ids,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
