package com.hama.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        // BCrypt 는 72바이트 뒤를 잘라서 버립니다. 상한을 둬서 그 차이를 사용자가 겪지 않게 합니다.
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 30, message = "닉네임은 30자 이하입니다.")
        String nickname
) {
    /** 대소문자만 다른 이메일로 중복 가입되지 않도록 소문자로 맞춥니다. */
    public SignupRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
