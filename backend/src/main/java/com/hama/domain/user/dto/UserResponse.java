package com.hama.domain.user.dto;

import com.hama.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 정보")
public record UserResponse(
        @Schema(description = "사용자 id", example = "1")
        Long id,

        @Schema(description = "이메일", example = "hama@example.com")
        String email,

        @Schema(description = "이름", example = "하마")
        String name,

        @Schema(description = "남은 크레딧(횟수)", example = "1")
        int credits
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCredits());
    }
}
