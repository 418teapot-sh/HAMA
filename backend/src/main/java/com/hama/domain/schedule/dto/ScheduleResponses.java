package com.hama.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public final class ScheduleResponses {

    private ScheduleResponses() {
    }

    @Schema(name = "ScheduleCreated")
    public record Created(Long scheduleId) {
    }

    @Schema(name = "ScheduleDetail")
    public record Detail(
            Long scheduleId,
            ScheduleType type,
            String title,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startAt,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endAt,
            boolean allDay,
            String repeatRule,
            String memo
    ) {
        public static Detail from(Schedule schedule) {
            return new Detail(schedule.getId(), schedule.getType(), schedule.getTitle(), schedule.getStartAt(),
                    schedule.getEndAt(), schedule.isAllDay(), schedule.getRepeatRule(), schedule.getMemo());
        }
    }
}
