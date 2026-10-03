package com.hama.domain.todo.entity;

import com.hama.domain.todo.exception.TodoErrorCode;
import com.hama.global.entity.BaseTimeEntity;
import com.hama.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "todo", indexes = @Index(name = "idx_todo_user_date_deleted",
        columnList = "user_id,todo_date,deleted_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "todo_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TodoCategory category;

    @Column(nullable = false, length = 255)
    private String content;

    @Column(name = "todo_date", nullable = false)
    private LocalDate todoDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TodoStatus status;

    @Column(name = "status_note", columnDefinition = "TEXT")
    private String statusNote;

    @Column(name = "postponed_count", nullable = false)
    private int postponedCount;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 목표에 연결되지 않은 일반 TASK를 생성합니다. 목표 투두 생성은 BE2 계약 확정 후 추가합니다. */
    public static Todo createTask(Long userId, String content, LocalDate todoDate,
            LocalTime startTime, LocalTime endTime) {
        if (userId == null || !isStorableDate(todoDate)) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_INPUT);
        }
        validateContent(content);
        validateTimes(startTime, endTime);
        Todo todo = new Todo();
        todo.userId = userId;
        todo.category = TodoCategory.TASK;
        todo.content = content;
        todo.todoDate = todoDate;
        todo.startTime = startTime;
        todo.endTime = endTime;
        todo.status = TodoStatus.PENDING;
        todo.postponedCount = 0;
        return todo;
    }

    public void revise(String content, LocalTime startTime, LocalTime endTime) {
        requirePending();
        validateContent(content);
        validateTimes(startTime, endTime);
        this.content = content;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void postpone(LocalDate targetDate, LocalTime startTime, LocalTime endTime) {
        requirePending();
        if (!isStorableDate(targetDate) || !targetDate.isAfter(todoDate)) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_POSTPONE_DATE);
        }
        validateTimes(startTime, endTime);
        this.todoDate = targetDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.postponedCount++;
    }

    public void complete(LocalDateTime now) {
        requirePending();
        this.status = TodoStatus.COMPLETED;
        this.completedAt = now;
    }

    public void changeStatusNote(String note) {
        if (note != null && note.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BusinessException(TodoErrorCode.TODO_NOTE_TOO_LONG);
        }
        this.statusNote = note == null || note.isEmpty() ? null : note;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    private void requirePending() {
        if (status == TodoStatus.COMPLETED) {
            throw new BusinessException(TodoErrorCode.TODO_ALREADY_COMPLETED);
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank() || content.length() > 255) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_INPUT);
        }
    }

    private static boolean isStorableDate(LocalDate date) {
        return date != null && date.getYear() >= 1000 && date.getYear() <= 9999;
    }

    private static void validateTimes(LocalTime start, LocalTime end) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null || !start.isBefore(end)
                || start.getSecond() != 0 || end.getSecond() != 0
                || start.getNano() != 0 || end.getNano() != 0) {
            throw new BusinessException(TodoErrorCode.TODO_INVALID_TIME);
        }
    }
}
