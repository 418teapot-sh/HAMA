package com.hama.domain.goalai.dto;

import com.hama.domain.shared.json.StrictDeserializers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class ReviewRequests {

    private ReviewRequests() {
    }

    @Schema(description = "목표 종료 AI 리뷰 생성")
    public record Create(
            @NotNull(message = "목표 id 는 필수입니다.") @Positive(message = "목표 id 가 올바르지 않습니다.")
            @Schema(example = "3") Long goalId,
            @Size(max = 200, message = "현재 상태는 200자 이하입니다.")
            @JsonDeserialize(using = StrictDeserializers.Text.class)
            @Schema(description = "선택. 지금 상태를 한 줄로(예: 체중 78kg, 토익 820점).", example = "체중 78kg", maxLength = 200)
            String currentStatus
    ) {
    }
}
