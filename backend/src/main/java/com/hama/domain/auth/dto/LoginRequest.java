package com.hama.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
    /** 가입 때와 같은 규칙으로 정규화해야 조회가 맞습니다(SignupRequest 참고). */
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
