package com.hama.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import tools.jackson.databind.annotation.JsonDeserialize;

/** 생략한 시간은 유지하고, 명시적 null은 지웁니다. 병합한 시간 쌍은 엔티티가 검증합니다. */
public abstract class TodoTimePatch {

    @JsonFormat(pattern = "HH:mm")
    @JsonDeserialize(using = StrictDeserializers.Time.class)
    @Schema(type = "string", example = "19:00", description = "생략 시 유지, null이면 지움")
    private LocalTime startTime;
    @JsonFormat(pattern = "HH:mm")
    @JsonDeserialize(using = StrictDeserializers.Time.class)
    @Schema(type = "string", example = "20:00", description = "생략 시 유지, null이면 지움")
    private LocalTime endTime;
    private boolean startTimePresent;
    private boolean endTimePresent;

    @JsonSetter("startTime")
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
        this.startTimePresent = true;
    }

    @JsonSetter("endTime")
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
        this.endTimePresent = true;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean hasTimePatch() {
        return startTimePresent || endTimePresent;
    }

    public LocalTime startOr(LocalTime original) {
        return startTimePresent ? startTime : original;
    }

    public LocalTime endOr(LocalTime original) {
        return endTimePresent ? endTime : original;
    }
}
