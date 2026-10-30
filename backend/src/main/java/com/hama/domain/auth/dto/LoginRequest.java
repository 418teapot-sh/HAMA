package com.hama.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(description = "가입한 이메일. 대소문자는 구분하지 않습니다.", example = "hama@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Pattern(regexp = SignupRequest.ASCII_ONLY, message = "이메일은 영문·숫자·기호로만 입력해주세요.")
        String email,

        @Schema(description = "비밀번호", example = "password1234", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
    /** 가입 때와 같은 규칙으로 정규화해야 조회가 맞습니다(SignupRequest 참고). */
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
