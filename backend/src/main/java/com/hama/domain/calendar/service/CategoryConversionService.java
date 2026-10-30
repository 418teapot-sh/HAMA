package com.hama.domain.calendar.service;

import com.hama.domain.calendar.dto.CategoryConversionResponse;
import com.hama.domain.calendar.dto.ConvertCategoryRequest;
import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.calendar.exception.CalendarErrorCode;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import com.hama.domain.schedule.exception.ScheduleErrorCode;
import com.hama.domain.schedule.repository.ScheduleRepository;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import com.hama.domain.todo.exception.TodoErrorCode;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 원본 행을 잠그고 새 항목 생성과 원본 소프트 삭제를 같은 트랜잭션에서 처리합니다. */
@Service
public class CategoryConversionService {
    private final TodoRepository todos;
    private final ScheduleRepository schedules;
    private final Clock clock;

    public CategoryConversionService(TodoRepository todos, ScheduleRepository schedules,
            @Qualifier("be3Clock") Clock clock) {
        this.todos = todos;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CategoryConversionResponse convert(Long userId, ConvertCategoryRequest request) {
        CalendarType source = type(request.sourceType());
        CalendarType target = type(request.targetType());
        if (source == target) throw invalid();
        if (source == CalendarType.PERSONAL) return fromSchedule(userId, request, source, target);
        var link = todos.findActiveLink(request.refId())
                .orElseThrow(() -> new BusinessException(TodoErrorCode.TODO_NOT_FOUND));
        requireOwner(link.getUserId(), userId);
        if (link.getGoalId() != null) throw notAllowed();
        Todo todo = todos.findActiveForUpdate(request.refId())
                .orElseThrow(() -> new BusinessException(TodoErrorCode.TODO_NOT_FOUND));
        requireOwner(todo.getUserId(), userId);
        todo.requireConvertible();
        if (!todo.getCategory().name().equals(source.name())) throw invalid();
        if (target != CalendarType.PERSONAL) {
            if (request.startAt() != null || request.endAt() != null || request.todoDate() != null
                    || request.startTime() != null || request.endTime() != null) throw invalid();
            todo.changeCategory(TodoCategory.valueOf(target.name()));
            return new CategoryConversionResponse(source, todo.getId(), target, todo.getId());
        }
        if (request.startAt() == null || request.endAt() == null || request.todoDate() != null
                || request.startTime() != null || request.endTime() != null) throw invalid();
        Schedule schedule = Schedule.create(userId, ScheduleType.PERSONAL, todo.getContent(),
                request.startAt(), request.endAt(), false, null, todo.getStatusNote());
        schedules.save(schedule);
        todo.delete(now());
        return new CategoryConversionResponse(source, todo.getId(), target, schedule.getId());
    }

    private CategoryConversionResponse fromSchedule(Long userId, ConvertCategoryRequest request,
            CalendarType source, CalendarType target) {
        Schedule schedule = schedules.findActiveForUpdate(request.refId())
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        requireOwner(schedule.getUserId(), userId);
        if (schedule.getType() != ScheduleType.PERSONAL || schedule.getRepeatRule() != null) throw notAllowed();
        if (target == CalendarType.PERSONAL || request.todoDate() == null
                || request.startAt() != null || request.endAt() != null) throw invalid();
        Todo todo = target == CalendarType.TASK
                ? Todo.createTask(userId, schedule.getTitle(), request.todoDate(), request.startTime(), request.endTime())
                : Todo.createOther(userId, schedule.getTitle(), request.todoDate(), request.startTime(), request.endTime());
        todo.changeStatusNote(schedule.getMemo());
        todos.save(todo);
        schedule.delete(now());
        return new CategoryConversionResponse(source, schedule.getId(), target, todo.getId());
    }

    private static CalendarType type(String type) {
        if (!"TASK".equals(type) && !"OTHER".equals(type) && !"PERSONAL".equals(type)) throw invalid();
        return CalendarType.valueOf(type);
    }

    private static void requireOwner(Long owner, Long user) {
        if (!owner.equals(user)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }
    private static BusinessException invalid() { return new BusinessException(CalendarErrorCode.CALENDAR_CONVERSION_INVALID); }
    private static BusinessException notAllowed() { return new BusinessException(CalendarErrorCode.CALENDAR_CONVERSION_NOT_ALLOWED); }
}
