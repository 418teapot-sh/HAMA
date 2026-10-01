package com.hama.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Schema(description = "회원가입 요청")
public record SignupRequest(
        @Schema(description = "이메일. 앞뒤 공백을 지우고 소문자로 저장합니다.", example = "hama@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        String email,

        @Schema(description = "비밀번호 (8~64자)", example = "password1234",
                minLength = 8, maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호는 필수입니다.")
        // BCrypt 는 72바이트 뒤를 잘라서 버립니다. 상한을 둬서 그 차이를 사용자가 겪지 않게 합니다.
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.")
        String password,

        @Schema(description = "이름 (50자 이하)", example = "하마", maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하입니다.")
        String name
) {
    /** 대소문자만 다른 이메일로 중복 가입되지 않도록 소문자로 맞춥니다. */
    public SignupRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
