package com.hama.domain.user.dto;

import com.hama.domain.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        boolean isPremium
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.isPremium());
    }
}
