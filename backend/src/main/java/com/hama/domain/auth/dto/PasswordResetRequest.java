package com.hama.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

@Schema(description = "비밀번호 재설정 메일 요청")
public record PasswordResetRequest(
        @Schema(description = "가입한 이메일", example = "hama@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Pattern(regexp = SignupRequest.ASCII_ONLY, message = "이메일은 영문·숫자·기호로만 입력해주세요.")
        String email
) {
    /** 가입 때처럼 소문자로 맞춰야 같은 계정을 찾습니다(SignupRequest 참고). */
    public PasswordResetRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
