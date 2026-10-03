package com.hama.domain.todo.service;

import com.hama.domain.todo.dto.PostponeTodoRequest;
import com.hama.domain.todo.dto.TodoResponses;
import com.hama.domain.todo.dto.UpdateTodoNoteRequest;
import com.hama.domain.todo.dto.UpdateTodoRequest;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import com.hama.domain.todo.exception.TodoErrorCode;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final Clock clock;

    public TodoService(TodoRepository todoRepository, @Qualifier("todoClock") Clock clock) {
        this.todoRepository = todoRepository;
        this.clock = clock;
    }

    public TodoResponses.Daily list(Long userId, LocalDate date, TodoCategory category) {
        LocalDate requestedDate = date == null ? LocalDate.now(clock) : date;
        return TodoResponses.Daily.from(requestedDate,
                todoRepository.findActiveByDate(userId, requestedDate, category));
    }

    @Transactional
    public TodoResponses.Created createTask(Long userId, String content, LocalDate date,
            LocalTime startTime, LocalTime endTime) {
        Todo todo = todoRepository.save(Todo.createTask(userId, content, date, startTime, endTime));
        return new TodoResponses.Created(todo.getId());
    }

    @Transactional
    public TodoResponses.Updated update(Long userId, Long todoId, UpdateTodoRequest request) {
        Todo todo = ownedForUpdate(userId, todoId);
        todo.revise(request.getContent() == null ? todo.getContent() : request.getContent(),
                request.startOr(todo.getStartTime()), request.endOr(todo.getEndTime()));
        return TodoResponses.Updated.from(todo);
    }

    @Transactional
    public TodoResponses.Postponed postpone(Long userId, Long todoId, PostponeTodoRequest request) {
        Todo todo = ownedForUpdate(userId, todoId);
        PostponeTodoRequest patch = request == null ? new PostponeTodoRequest() : request;
        LocalDate target = patch.getTargetDate() == null
                ? todo.getTodoDate().plusDays(1) : patch.getTargetDate();
        todo.postpone(target, patch.startOr(todo.getStartTime()), patch.endOr(todo.getEndTime()));
        return TodoResponses.Postponed.from(todo);
    }

    @Transactional
    public TodoResponses.Completion complete(Long userId, Long todoId) {
        Todo todo = ownedForUpdate(userId, todoId);
        todo.complete(now());
        return TodoResponses.Completion.from(todo);
    }

    @Transactional
    public TodoResponses.Note updateNote(Long userId, Long todoId, UpdateTodoNoteRequest request) {
        Todo todo = ownedForUpdate(userId, todoId);
        if (request.hasStatusNote()) {
            todo.changeStatusNote(request.getStatusNote());
        }
        return new TodoResponses.Note(todo.getId(), todo.getStatusNote());
    }

    @Transactional
    public void delete(Long userId, Long todoId) {
        ownedForUpdate(userId, todoId).delete(now());
    }

    private Todo ownedForUpdate(Long userId, Long todoId) {
        Todo todo = todoRepository.findActiveForUpdate(todoId)
                .orElseThrow(() -> new BusinessException(TodoErrorCode.TODO_NOT_FOUND));
        if (!todo.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return todo;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
