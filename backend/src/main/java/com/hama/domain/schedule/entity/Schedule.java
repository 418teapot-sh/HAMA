package com.hama.domain.schedule.entity;

import com.hama.domain.schedule.exception.ScheduleErrorCode;
import com.hama.global.entity.BaseTimeEntity;
import com.hama.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule", indexes = @Index(name = "idx_schedule_user_start_deleted",
        columnList = "user_id,start_at,deleted_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ScheduleType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "repeat_rule", length = 255)
    private String repeatRule;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Schedule create(Long userId, ScheduleType type, String title, LocalDateTime startAt,
            LocalDateTime endAt, boolean allDay, String repeatRule, String memo) {
        if (userId == null) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_INVALID_INPUT);
        }
        Schedule schedule = new Schedule();
        schedule.userId = userId;
        schedule.revise(type, title, startAt, endAt, allDay, repeatRule, memo);
        return schedule;
    }

    /** 반복 일정은 개별 발생 건이 아닌 시리즈 전체를 수정합니다. 검증 후에만 상태를 바꿉니다. */
    public void revise(ScheduleType type, String title, LocalDateTime startAt, LocalDateTime endAt,
            boolean allDay, String repeatRule, String memo) {
        if (type == null || title == null || title.isBlank() || title.length() > 100) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_INVALID_INPUT);
        }
        if (!isStorableTime(startAt) || !isStorableTime(endAt) || !startAt.isBefore(endAt)) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_INVALID_TIME);
        }
        if (allDay && (!startAt.toLocalTime().equals(LocalTime.MIDNIGHT)
                || !endAt.toLocalTime().equals(LocalTime.MIDNIGHT))) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_INVALID_ALL_DAY);
        }
        if (repeatRule != null) {
            ScheduleRepeatRule.parse(repeatRule, startAt, allDay);
        }
        if (memo != null && memo.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_MEMO_TOO_LONG);
        }
        this.type = type;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.repeatRule = repeatRule;
        this.memo = memo;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    private static boolean isStorableTime(LocalDateTime time) {
        return time != null && time.getYear() >= 1000 && time.getYear() <= 9999 && time.getNano() == 0;
    }
}
