package com.hama.domain.auth.dto;

import static com.hama.domain.auth.dto.SignupRequest.PASSWORD_RULE;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "새 비밀번호 설정")
public record PasswordResetConfirmRequest(
        @Schema(description = "재설정 링크의 ?token= 값", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "토큰은 필수입니다.")
        String token,

        @Schema(description = "새 비밀번호. 회원가입과 같은 규칙(영문·숫자·특수문자 포함 8~20자)",
                example = "newPassword1234!", minLength = 8, maxLength = 20, pattern = PASSWORD_RULE,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = PASSWORD_RULE, message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8~20자로 작성해주세요.")
        String newPassword
) {
}
