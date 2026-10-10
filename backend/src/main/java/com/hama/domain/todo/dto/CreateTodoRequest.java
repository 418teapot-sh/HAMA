package com.hama.domain.todo.dto;

import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "일반 TASK 또는 목표 연결 AI_GOAL_TASK 생성.")
@ValidTodoGoalLink
public record CreateTodoRequest(
        @NotNull(message = "카테고리는 필수입니다.")
        @Pattern(regexp = "TASK|AI_GOAL_TASK", message = "TASK 또는 AI_GOAL_TASK를 입력해야 합니다.")
        @JsonDeserialize(using = StrictDeserializers.Text.class)
        @Schema(example = "TASK", allowableValues = {"TASK", "AI_GOAL_TASK"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String category,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 255, message = "내용은 255자 이하입니다.")
        @JsonDeserialize(using = StrictDeserializers.Text.class)
        @Schema(example = "책 읽기", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @NotNull(message = "투두 날짜는 필수입니다.")
        @JsonDeserialize(using = StrictDeserializers.Date.class)
        @Schema(example = "2026-10-04", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate todoDate,

        @JsonDeserialize(using = StrictDeserializers.Time.class)
        @Schema(type = "string", example = "19:00", description = "종료 시간과 함께 지정하거나 둘 다 비웁니다.")
        LocalTime startTime,

        @JsonDeserialize(using = StrictDeserializers.Time.class)
        @Schema(type = "string", example = "20:00")
        LocalTime endTime,

        @Positive
        @Schema(description = "AI_GOAL_TASK 필수. TASK는 null.")
        Long goalId,

        @Positive
        @Schema(description = "AI_GOAL_TASK의 선택 기간 목표. TASK는 null.")
        Long periodGoalId
) {
}
