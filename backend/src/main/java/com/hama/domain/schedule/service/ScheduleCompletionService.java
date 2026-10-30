package com.hama.domain.schedule.service;

import com.hama.domain.schedule.dto.ScheduleCompletionRequest;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleCompletion;
import com.hama.domain.schedule.exception.ScheduleErrorCode;
import com.hama.domain.schedule.recurrence.ScheduleRecurrence;
import com.hama.domain.schedule.repository.ScheduleCompletionRepository;
import com.hama.domain.schedule.repository.ScheduleRepository;
import com.hama.domain.todo.entity.TodoStatus;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleCompletionService {
    private final UserRepository users;
    private final ScheduleRepository schedules;
    private final ScheduleCompletionRepository completions;
    private final Clock clock;
    private final ScheduleRecurrence recurrence = new ScheduleRecurrence();

    public ScheduleCompletionService(UserRepository users, ScheduleRepository schedules,
            ScheduleCompletionRepository completions, @Qualifier("be3Clock") Clock clock) {
        this.users = users;
        this.schedules = schedules;
        this.completions = completions;
        this.clock = clock;
    }

    public record Result(long scheduleId, LocalDate date,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime occurrenceStartAt,
            TodoStatus status, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime completedAt) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result complete(long userId, long scheduleId, ScheduleCompletionRequest request) {
        Schedule schedule = lockAndValidate(userId, scheduleId, request);
        LocalDate occurrenceDate = request.occurrenceStartAt().toLocalDate();
        if (completions.findByScheduleIdAndOccurrenceDateAndDisplayDate(schedule.getId(), occurrenceDate, request.date()).isPresent()) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_ALREADY_COMPLETED);
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        completions.save(ScheduleCompletion.create(userId, scheduleId, occurrenceDate, request.date(), now));
        return new Result(scheduleId, request.date(), request.occurrenceStartAt(), TodoStatus.COMPLETED, now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result uncomplete(long userId, long scheduleId, ScheduleCompletionRequest request) {
        Schedule schedule = lockAndValidate(userId, scheduleId, request);
        ScheduleCompletion completion = completions.findByScheduleIdAndOccurrenceDateAndDisplayDate(
                schedule.getId(), request.occurrenceStartAt().toLocalDate(), request.date())
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_COMPLETED));
        completions.delete(completion);
        return new Result(scheduleId, request.date(), request.occurrenceStartAt(), TodoStatus.PENDING, null);
    }

    private Schedule lockAndValidate(long userId, long scheduleId, ScheduleCompletionRequest request) {
        // 회원탈퇴의 사용자 → 일정 잠금 순서와 맞추어 삭제 후 고아 기록이 생기지 않게 합니다.
        users.findByIdForShare(userId).orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        Schedule schedule = schedules.findActiveForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.getUserId().equals(userId)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        if (request.date().getYear() < 1000 || request.date().getYear() > 9999
                || request.occurrenceStartAt().getYear() < 1000 || request.occurrenceStartAt().getYear() > 9999
                || !recurrence.hasOccurrence(new ScheduleRecurrence.Source(schedule.getStartAt(), schedule.getEndAt(),
                        schedule.isAllDay(), schedule.getRepeatRule()), request.date(), request.occurrenceStartAt())) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_OCCURRENCE_NOT_FOUND);
        }
        return schedule;
    }
}
