package com.hama.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Schema(description = "회원가입 요청")
public record SignupRequest(
        @Schema(description = "이메일. 앞뒤 공백을 지우고 소문자로 저장합니다.", example = "hama@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        String email,

        @Schema(description = "비밀번호. 영문·숫자·특수문자를 각각 1개 이상 포함한 8~20자 (공백·한글 불가)",
                example = "password1234!", minLength = 8, maxLength = 20, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = PASSWORD_RULE, message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8~20자로 작성해주세요.")
        String password,

        @Schema(description = "이름 (50자 이하)", example = "하마", maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하입니다.")
        String name,

        @Schema(description = "서비스 이용약관 동의 (필수, true 여야 가입)", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "서비스 이용약관에 동의해주세요.")
        @AssertTrue(message = "서비스 이용약관에 동의해주세요.")
        Boolean termsAgreed,

        @Schema(description = "개인정보 수집 및 이용 동의 (필수, true 여야 가입)", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "개인정보 수집 및 이용에 동의해주세요.")
        @AssertTrue(message = "개인정보 수집 및 이용에 동의해주세요.")
        Boolean privacyAgreed,

        @Schema(description = "만 14세 이상 확인 (필수, true 여야 가입)", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "만 14세 이상만 가입할 수 있습니다.")
        @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
        Boolean ageConfirmed,

        @Schema(description = "마케팅 알림 수신 동의 (선택, 생략하면 false)", example = "false")
        Boolean marketingAgreed
) {
    /** 영문·숫자·특수문자(ASCII 기호)를 각각 하나 이상, 출력 가능한 ASCII 만 8~20자. 디자인의 회원가입 화면 규칙입니다. */
    public static final String PASSWORD_RULE =
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])[!-~]{8,20}$";

    /** 대소문자만 다른 이메일로 중복 가입되지 않도록 소문자로 맞춥니다. */
    public SignupRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
