package com.hama.domain.calendar.service;

import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleRepeatRule;
import com.hama.domain.schedule.exception.ScheduleErrorCode;
import com.hama.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import com.hama.domain.schedule.recurrence.ScheduleRecurrence;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** 저장 시 종료 경계 계산과 동일한 반복 엔진을 사용합니다. */
@Component
@Slf4j
public final class ScheduleOccurrences {
    private final ScheduleRecurrence recurrence = new ScheduleRecurrence();

    public record Occurrence(LocalDateTime startAt, LocalDateTime endAt) {
    }

    /** 반복 규칙 자체를 해석할 수 없는 저장 일정만 제외합니다. 원문은 로그에 남기지 않습니다. */
    public boolean hasReadableRepeatRule(long scheduleId, ScheduleRecurrence.Source source) {
        if (source.repeatRule() == null) return true;
        try {
            ScheduleRepeatRule.parse(source.repeatRule(), source.startAt(), source.allDay());
            return true;
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ScheduleErrorCode.SCHEDULE_INVALID_REPEAT_RULE
                    && exception.getErrorCode() != ScheduleErrorCode.SCHEDULE_REPEAT_START_MISMATCH) throw exception;
            log.warn("Skipping schedule with unreadable repeat rule: scheduleId={}, errorCode={}",
                    scheduleId, exception.getErrorCode());
            return false;
        }
    }

    public List<Occurrence> between(Schedule schedule, LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        forEach(schedule, from, to, result::add);
        return List.copyOf(result);
    }

    public void forEach(Schedule schedule, LocalDate from, LocalDate to, Consumer<Occurrence> consumer) {
        if (schedule.getDeletedAt() == null) {
            forEach(source(schedule), from, to, consumer);
        }
    }

    public void forEach(ScheduleRecurrence.Source source, LocalDate from, LocalDate to, Consumer<Occurrence> consumer) {
        recurrence.forEach(source, from, to, occurrence -> consumer.accept(new Occurrence(occurrence.startAt(), occurrence.endAt())));
    }

    public boolean overlaps(Schedule schedule, LocalDate from, LocalDate to) {
        return schedule.getDeletedAt() == null && overlaps(source(schedule), from, to);
    }

    public boolean overlaps(ScheduleRecurrence.Source source, LocalDate from, LocalDate to) {
        return recurrence.overlaps(source, from, to);
    }

    private ScheduleRecurrence.Source source(Schedule schedule) {
        return new ScheduleRecurrence.Source(schedule.getStartAt(), schedule.getEndAt(), schedule.isAllDay(), schedule.getRepeatRule());
    }
}
