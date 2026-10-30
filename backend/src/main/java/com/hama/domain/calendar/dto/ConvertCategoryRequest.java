package com.hama.domain.calendar.dto;

import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "TASK/OTHER ↔ 반복 없는 PERSONAL 일정 전환. 목표 투두·FIXED·반복 일정은 전환하지 않습니다.")
public record ConvertCategoryRequest(
        @NotNull @Pattern(regexp = "TASK|OTHER|PERSONAL")
        @JsonDeserialize(using = StrictDeserializers.Text.class) String sourceType,
        @NotNull @Positive Long refId,
        @NotNull @Pattern(regexp = "TASK|OTHER|PERSONAL")
        @JsonDeserialize(using = StrictDeserializers.Text.class) String targetType,
        @Schema(description = "PERSONAL로 전환 시 필수. KST 시작 일시")
        @JsonDeserialize(using = StrictDeserializers.DateTime.class) LocalDateTime startAt,
        @Schema(description = "PERSONAL로 전환 시 필수. KST 종료 일시")
        @JsonDeserialize(using = StrictDeserializers.DateTime.class) LocalDateTime endAt,
        @Schema(description = "PERSONAL에서 TASK/OTHER로 전환 시 필수")
        @JsonDeserialize(using = StrictDeserializers.Date.class) LocalDate todoDate,
        @Schema(description = "PERSONAL에서 TASK/OTHER로 전환 시 선택. 시작·종료 함께 입력하거나 둘 다 생략")
        @JsonDeserialize(using = StrictDeserializers.Time.class) LocalTime startTime,
        @JsonDeserialize(using = StrictDeserializers.Time.class) LocalTime endTime
) {
}
