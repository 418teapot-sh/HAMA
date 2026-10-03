package com.hama.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "고정·개인 일정 생성. startAt/endAt은 KST이며 종료 경계는 포함하지 않습니다.")
@Getter
public class CreateScheduleRequest {
    @NotNull(message = "일정 타입은 필수입니다.")
    @Pattern(regexp = "FIXED|PERSONAL", message = "일정 타입은 FIXED 또는 PERSONAL입니다.")
    @JsonDeserialize(using = ScheduleDeserializers.Text.class)
    @Schema(allowableValues = {"FIXED", "PERSONAL"}, example = "FIXED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이하입니다.")
    @JsonDeserialize(using = ScheduleDeserializers.Text.class)
    @Schema(example = "회사", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotNull(message = "시작 일시는 필수입니다.")
    @JsonDeserialize(using = ScheduleDeserializers.DateTime.class)
    @Schema(type = "string", example = "2026-10-05T09:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startAt;

    @NotNull(message = "종료 일시는 필수입니다.")
    @JsonDeserialize(using = ScheduleDeserializers.DateTime.class)
    @Schema(type = "string", example = "2026-10-05T18:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endAt;

    @JsonSetter(nulls = Nulls.FAIL)
    @JsonDeserialize(using = ScheduleDeserializers.Bool.class)
    @Schema(description = "생략 시 false. null은 불가. true이면 시작·종료 모두 00:00이어야 합니다.", defaultValue = "false")
    private Boolean allDay = false;

    @Size(max = 255, message = "반복 규칙은 255자 이하입니다.")
    @JsonDeserialize(using = ScheduleDeserializers.Text.class)
    @Schema(description = "FREQ=DAILY/WEEKLY, INTERVAL, COUNT 또는 UNTIL, WEEKLY의 BYDAY 지원. "
            + "종일 UNTIL=YYYYMMDD, 시간 일정 UNTIL=YYYYMMDDTHHMMSSZ(UTC). "
            + "BYDAY에는 시작 요일을 포함합니다. 미지원 옵션은 400, null은 반복 없음.",
            example = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", maxLength = 255)
    private String repeatRule;

    @JsonDeserialize(using = ScheduleDeserializers.Text.class)
    @Schema(description = "선택 메모. UTF-8 기준 최대 65535바이트.", nullable = true)
    private String memo;
}
