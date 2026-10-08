package com.hama.domain.checkin.dto;

import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateCheckinRequest(
        @NotNull @Positive Long goalId,
        @NotNull @JsonDeserialize(using = CheckinType.Deserializer.class) CheckinType type,
        @Digits(integer = 8, fraction = 2) BigDecimal value,
        @JsonDeserialize(using = StrictDeserializers.Text.class) String note,
        @Schema(description = "END에서만 허용. 목표 상태는 변경하지 않습니다.")
        @JsonDeserialize(using = StrictDeserializers.Bool.class) Boolean achieved,
        @Schema(description = "생략 시 현재 KST. 미래 시각은 거절.", example = "2026-10-08T12:00:00")
        @JsonDeserialize(using = StrictDeserializers.DateTime.class) LocalDateTime checkedAt
) {}
