package com.hama.domain.goal.dto;

import java.time.LocalDateTime;

public record CurrentStatusResponse(Long goalId, String currentStatus, LocalDateTime updatedAt) {
}
