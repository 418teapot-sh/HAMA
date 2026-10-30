package com.hama.domain.schedule.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 일정 발생 건의 표시 날짜별 완료 기록입니다. */
@Entity
@Table(name = "schedule_completion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleCompletion extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "completion_id")
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;
    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;
    @Column(name = "display_date", nullable = false)
    private LocalDate displayDate;
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    public static ScheduleCompletion create(long userId, long scheduleId, LocalDate occurrenceDate,
            LocalDate displayDate, LocalDateTime completedAt) {
        ScheduleCompletion completion = new ScheduleCompletion();
        completion.userId = userId;
        completion.scheduleId = scheduleId;
        completion.occurrenceDate = java.util.Objects.requireNonNull(occurrenceDate);
        completion.displayDate = java.util.Objects.requireNonNull(displayDate);
        completion.completedAt = java.util.Objects.requireNonNull(completedAt);
        return completion;
    }
}
