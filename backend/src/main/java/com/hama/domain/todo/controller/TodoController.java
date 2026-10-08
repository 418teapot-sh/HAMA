package com.hama.domain.todo.controller;

import com.hama.domain.todo.dto.CreateTodoRequest;
import com.hama.domain.todo.dto.PostponeTodoRequest;
import com.hama.domain.todo.dto.TodoResponses;
import com.hama.domain.todo.dto.UpdateTodoNoteRequest;
import com.hama.domain.todo.dto.UpdateTodoRequest;
import com.hama.domain.todo.entity.TodoCategory;
import com.hama.domain.todo.service.TodoService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "투두", description = "일반 TASK 및 목표 투두 관리. AI 미루기는 후속 범위입니다.")
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @Operation(summary = "투두 생성", description = "TASK는 목표 ID가 없어야 합니다. AI_GOAL_TASK는 진행 중인 본인 목표와 기간 안의 날짜가 필요하며 periodGoalId는 선택입니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<TodoResponses.Created>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateTodoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                todoService.create(authUser.userId(), request)));
    }

    @Operation(summary = "날짜별 투두 조회", description = "날짜 생략 시 KST 오늘. 카테고리 생략 시 전체.")
    @GetMapping
    public ApiResponse<TodoResponses.Daily> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TodoCategory category) {
        return ApiResponse.success(todoService.list(authUser.userId(), date, category));
    }

    @Operation(summary = "투두 수정", description = "완료된 투두는 409. 생략한 필드는 유지합니다.")
    @PatchMapping("/{todoId}")
    public ApiResponse<TodoResponses.Updated> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long todoId, @Valid @RequestBody UpdateTodoRequest request) {
        return ApiResponse.success(todoService.update(authUser.userId(), todoId, request));
    }

    @Operation(summary = "일반 투두 미루기",
            description = "기존 날짜 이후로만 미룰 수 있습니다. 본문/날짜 생략 시 기존 날짜 +1일, 시간 생략 시 유지. AI_GOAL_TASK는 현재 409 TODO_AI_POSTPONE_NOT_SUPPORTED.")
    @PatchMapping("/{todoId}/postpone")
    public ApiResponse<TodoResponses.Postponed> postpone(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long todoId, @Valid @RequestBody(required = false) PostponeTodoRequest request) {
        return ApiResponse.success(todoService.postpone(authUser.userId(), todoId, request));
    }

    @Operation(summary = "투두 완료", description = "재완료는 409. 일반 TASK의 goalProgressRate는 null, 목표 투두는 활성 투두 완료 비율(소수 첫째 자리)입니다.")
    @PatchMapping("/{todoId}/complete")
    public ApiResponse<TodoResponses.Completion> complete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long todoId) {
        return ApiResponse.success(todoService.complete(authUser.userId(), todoId));
    }

    @Operation(summary = "투두 상태 메모 수정",
            description = "미완료·완료 투두 모두 가능. 생략 시 유지, 빈 문자열/null이면 메모 삭제.")
    @PatchMapping("/state/{todoId}")
    public ApiResponse<TodoResponses.Note> updateNote(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long todoId, @Valid @RequestBody UpdateTodoNoteRequest request) {
        return ApiResponse.success(todoService.updateNote(authUser.userId(), todoId, request));
    }

    @Operation(summary = "투두 삭제", description = "소프트 삭제. 성공 시 HTTP 200, 공통 응답의 data는 null.")
    @DeleteMapping("/{todoId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long todoId) {
        todoService.delete(authUser.userId(), todoId);
        return ApiResponse.noContent();
    }
}
