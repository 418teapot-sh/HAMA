package com.hama.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "TASK 미루기. 본문/날짜 생략 시 기존 투두 날짜 +1일, 시간 생략 시 유지.")
public class PostponeTodoRequest extends TodoTimePatch {

    @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    @JsonDeserialize(using = TodoDateDeserializer.class)
    @Schema(example = "2026-10-02", description = "기존 투두 날짜보다 뒤의 날짜. null은 허용하지 않습니다.")
    private LocalDate targetDate;

    @JsonSetter(value = "targetDate", nulls = Nulls.FAIL)
    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }
}
