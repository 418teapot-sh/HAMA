package com.hama.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import com.hama.domain.todo.entity.TodoStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class TodoResponses {

    private TodoResponses() {
    }

    public record Created(Long todoId) {
    }

    public record Daily(LocalDate date, List<Pending> pending, List<Completed> completed) {
        public static Daily from(LocalDate date, List<Todo> todos) {
            return new Daily(date,
                    todos.stream().filter(t -> t.getStatus() == TodoStatus.PENDING)
                            .map(Pending::from).toList(),
                    todos.stream().filter(t -> t.getStatus() == TodoStatus.COMPLETED)
                            .map(Completed::from).toList());
        }
    }

    public record Pending(Long todoId, TodoCategory category, String content,
            Long goalId, String goalTitle,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime, int postponedCount) {
        static Pending from(Todo todo) {
            return new Pending(todo.getId(), todo.getCategory(), todo.getContent(), null, null,
                    todo.getStartTime(), todo.getEndTime(), todo.getPostponedCount());
        }
    }

    public record Completed(Long todoId, TodoCategory category, String content,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime completedAt,
            String statusNote) {
        static Completed from(Todo todo) {
            return new Completed(todo.getId(), todo.getCategory(), todo.getContent(),
                    todo.getCompletedAt(), todo.getStatusNote());
        }
    }

    public record Updated(Long todoId, String content,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
        public static Updated from(Todo todo) {
            return new Updated(todo.getId(), todo.getContent(), todo.getStartTime(), todo.getEndTime());
        }
    }

    public record Postponed(Long todoId, LocalDate todoDate,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime, int postponedCount) {
        public static Postponed from(Todo todo) {
            return new Postponed(todo.getId(), todo.getTodoDate(), todo.getStartTime(),
                    todo.getEndTime(), todo.getPostponedCount());
        }
    }

    public record Completion(Long todoId, TodoStatus status,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime completedAt,
            BigDecimal goalProgressRate) {
        public static Completion from(Todo todo) {
            return new Completion(todo.getId(), todo.getStatus(), todo.getCompletedAt(), null);
        }
    }

    public record Note(Long todoId, String statusNote) {
    }
}
