package com.hama.domain.goal.dto;

import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "현재 상태만 바꿉니다. 다른 필드는 건드리지 않습니다. null 이면 지웁니다.")
public record UpdateCurrentStatusRequest(
        @Size(max = 200, message = "현재 상태는 200자 이하입니다.")
        @JsonDeserialize(using = StrictDeserializers.Text.class)
        @Schema(example = "토익 700점", maxLength = 200)
        String currentStatus
) {
}
