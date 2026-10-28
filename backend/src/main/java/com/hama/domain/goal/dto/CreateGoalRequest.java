package com.hama.domain.goal.dto;

import com.hama.domain.goal.entity.GoalContent;
import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "목표 직접 생성 (AI 없이).")
public record CreateGoalRequest(
        @NotBlank(message = "목표 제목은 필수입니다.")
        @Size(max = 100, message = "목표 제목은 100자 이하입니다.")
        @Schema(example = "매일 영어 공부", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Size(max = 200, message = "현재 상태는 200자 이하입니다.")
        @JsonDeserialize(using = StrictDeserializers.Text.class)
        @Schema(example = "토익 600점", maxLength = 200, description = "현재 상태를 직접 입력합니다. 생략 가능.")
        String currentStatus,

        @NotNull(message = "시작일은 필수입니다.")
        @Schema(example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,

        @NotNull(message = "종료일은 필수입니다.")
        @Schema(example = "2026-12-31", description = "오늘(KST) 이후여야 합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate endDate,

        @Valid
        @Size(max = 7, message = "고정 일정은 요일마다 하나씩, 최대 7개입니다.")
        @Schema(description = "요일마다 이미 바쁜 일정(예: 월요일 - 한국사 공부). A/B 플랜 생성 때 참고만 합니다. 생략 가능.")
        List<FixedSchedule> fixedSchedules
) {

    @Schema(description = "요일 하나와 그 요일에 이미 하는 일.")
    public record FixedSchedule(
            @NotNull(message = "요일은 필수입니다.")
            @Schema(example = "MONDAY", requiredMode = Schema.RequiredMode.REQUIRED)
            DayOfWeek dayOfWeek,

            @NotBlank(message = "일정 내용은 필수입니다.")
            @Size(max = 100, message = "일정 내용은 100자 이하입니다.")
            @JsonDeserialize(using = StrictDeserializers.Text.class)
            @Schema(example = "한국사 공부", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
            String content
    ) {
    }

    public GoalContent toContent() {
        return new GoalContent(title, currentStatus, startDate, endDate);
    }
}
