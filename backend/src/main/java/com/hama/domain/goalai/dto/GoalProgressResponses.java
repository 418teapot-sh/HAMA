package com.hama.domain.goalai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.todo.entity.Todo;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class GoalProgressResponses {
    private GoalProgressResponses() {
    }

    public record Record(Long todoId, Long goalId, String content, String statusNote, LocalDate todoDate,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime completedAt) {
        public static Record from(Todo todo) {
            return new Record(todo.getId(), todo.getGoalId(), todo.getContent(), todo.getStatusNote(),
                    todo.getTodoDate(), todo.getCompletedAt());
        }
    }

    public record Summary(Long goalId, String status, String summary,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime generatedAt,
            boolean stale, int usedRecordCount, long sourceRecordCount, long currentRecordCount,
            int attempts, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime nextAttemptAt) {
    }
}
