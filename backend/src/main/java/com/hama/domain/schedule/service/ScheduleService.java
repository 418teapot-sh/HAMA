package com.hama.domain.schedule.service;

import com.hama.domain.schedule.dto.CreateScheduleRequest;
import com.hama.domain.schedule.dto.ScheduleResponses;
import com.hama.domain.schedule.dto.UpdateScheduleRequest;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import com.hama.domain.schedule.exception.ScheduleErrorCode;
import com.hama.domain.schedule.repository.ScheduleRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository schedules;
    private final Clock clock;

    public ScheduleService(ScheduleRepository schedules, @Qualifier("be3Clock") Clock clock) {
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional
    public ScheduleResponses.Created create(Long userId, CreateScheduleRequest request) {
        Schedule schedule = schedules.save(Schedule.create(userId, ScheduleType.valueOf(request.getType()),
                request.getTitle(), request.getStartAt(), request.getEndAt(), Boolean.TRUE.equals(request.getAllDay()),
                request.getRepeatRule(), request.getMemo()));
        return new ScheduleResponses.Created(schedule.getId());
    }

    public ScheduleResponses.Detail get(Long userId, Long scheduleId) {
        Schedule schedule = schedules.findByIdAndDeletedAtIsNull(scheduleId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        requireOwner(schedule, userId);
        return ScheduleResponses.Detail.from(schedule);
    }

    @Transactional
    public ScheduleResponses.Detail update(Long userId, Long scheduleId, UpdateScheduleRequest request) {
        Schedule schedule = ownedForUpdate(userId, scheduleId);
        schedule.revise(request.getType() == null ? schedule.getType() : ScheduleType.valueOf(request.getType()),
                request.getTitle() == null ? schedule.getTitle() : request.getTitle(),
                request.getStartAt() == null ? schedule.getStartAt() : request.getStartAt(),
                request.getEndAt() == null ? schedule.getEndAt() : request.getEndAt(),
                request.getAllDay() == null ? schedule.isAllDay() : request.getAllDay(),
                request.repeatRuleOr(schedule.getRepeatRule()), request.memoOr(schedule.getMemo()));
        return ScheduleResponses.Detail.from(schedule);
    }

    @Transactional
    public void delete(Long userId, Long scheduleId) {
        ownedForUpdate(userId, scheduleId).delete(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }

    private Schedule ownedForUpdate(Long userId, Long scheduleId) {
        Schedule schedule = schedules.findActiveForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        requireOwner(schedule, userId);
        return schedule;
    }

    private void requireOwner(Schedule schedule, Long userId) {
        if (!schedule.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
