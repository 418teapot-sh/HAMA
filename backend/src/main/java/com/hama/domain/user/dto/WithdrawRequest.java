package com.hama.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원탈퇴 요청")
public record WithdrawRequest(
        @Schema(description = "본인 확인용 현재 비밀번호", example = "password1234",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
