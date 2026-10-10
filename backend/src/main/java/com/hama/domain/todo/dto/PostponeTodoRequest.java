package com.hama.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "TASK는 날짜 생략 시 다음 날. AI는 빈 요청 시 자동 배치, 직접 지정 시 targetDate 필수. 시간 생략은 유지, null은 지움.")
public class PostponeTodoRequest extends TodoTimePatch {

    @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    @JsonDeserialize(using = StrictDeserializers.Date.class)
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
