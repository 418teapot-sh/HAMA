package com.hama.domain.goalai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SessionRequests {

    private SessionRequests() {
    }

    @Schema(description = "목표 대화 세션 시작")
    public record Start(
            @NotBlank(message = "목표를 입력해주세요.")
            @Size(max = 500, message = "목표는 500자 이하입니다.")
            @Schema(example = "3개월 안에 토익 850점 받고 싶어.", maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
            String rawGoal
    ) {
    }

    @Schema(description = "AI 질문에 대한 답변")
    public record Message(
            @NotBlank(message = "답변을 입력해주세요.")
            @Size(max = 1000, message = "답변은 1000자 이하입니다.")
            @Schema(example = "지금 700점 정도예요.", maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
            String content
    ) {
    }
}
