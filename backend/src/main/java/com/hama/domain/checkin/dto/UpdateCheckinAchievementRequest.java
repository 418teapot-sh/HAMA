package com.hama.domain.checkin.dto;

import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateCheckinAchievementRequest(
        @NotNull
        @Schema(description = "END 달성 여부. true/false 필수이며 생략·null은 허용하지 않습니다.", example = "true")
        @JsonDeserialize(using = StrictDeserializers.Bool.class) Boolean achieved
) {}
