package com.hama.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 찾기 문의")
public record EmailInquiryRequest(
        @Schema(description = "가입할 때 쓴 이름 (50자 이하)", example = "하마", maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하입니다.")
        String name,

        @Schema(description = "답변받을 이메일 (가입 이메일이 아니어도 됨)", example = "reply@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하입니다.")
        String email
) {
    public EmailInquiryRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim();
    }
}
