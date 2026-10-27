package com.hama.domain.goalai.controller;

import com.hama.domain.goalai.dto.GoalProgressResponses;
import com.hama.domain.goalai.service.GoalProgressService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import com.hama.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표 진행 상태", description = "완료한 목표 투두별 현재 상태 기록과 무료 진행 요약. 종료 리뷰와 별개입니다.")
@RestController
@RequestMapping("/api/v1/goals/{goalId}")
@RequiredArgsConstructor
public class GoalProgressController {

    private final GoalProgressService service;

    @Operation(summary = "목표 현재 상태 기록 조회", description = """
            기존 PATCH /api/v1/todos/state/{todoId}로 입력·수정합니다. 상태 메모가 있는 활성 완료 AI 투두만 조회합니다.
            실제 완료 시각(KST)·ID 내림차순이며 글 수정 후에도 같은 완료일 아래에 유지합니다. 원문은 자르지 않습니다.
            page 기본0, size 기본20/최대100. 삭제·미완료·일반 TASK 제외. 없는/삭제 목표404, 타인403.
            """)
    @GetMapping("/progress-records")
    public ApiResponse<PageResponse<GoalProgressResponses.Record>> records(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user, @PathVariable Long goalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.records(user.userId(), goalId, PageResponse.pageRequest(page, size)));
    }

    @Operation(summary = "진행 중 AI 요약 조회", description = """
            상태 저장·목표/투두 입력 변경 뒤 비동기로 자동 갱신합니다. 추가 크레딧 차감 없음.
            전체 AI 투두 완료율과 최근 상태 기록20개(각300 Unicode 문자)를 사용하며 원문/전체 이력은 보존합니다.
            usedRecordCount/sourceRecordCount는 저장 요약의 사용/전체 기록 수, currentRecordCount는 현재 기록 수입니다.
            PENDING/GENERATING/READY/EMPTY/FAILED/INACTIVE 상태와 stale·generatedAt을 확인하세요.
            오류에도 원문/이전 요약을 보존합니다. GET은 누락·입력 변경을 감지해 갱신을 예약할 수 있으나 AI를 동기 호출하지 않습니다.
            배치 처리 후 기본5초 간격으로 DB 대기를 확인하고 같은 입력은 재사용합니다. 한 변경당 최대3회, 실패 간격30/60초입니다.
            서버 중단 예약은 max(120초, AI timeout+60초) 후 회수합니다. 종료/PLANNING은 새 생성 없이 기존 요약/이력만 조회합니다.
            종료 후 상태 글 수정은 보존하며 stale로 표시합니다. END achieved/목표 resultStatus를 변경하지 않습니다.
            없는/삭제 목표404, 타인403.
            """)
    @GetMapping("/progress-summary")
    public ApiResponse<GoalProgressResponses.Summary> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        return ApiResponse.success(service.get(user.userId(), goalId));
    }

    @Operation(summary = "진행 요약 재시도 예약", description = """
            FAILED 상태의 명시적 무료 재시도. 원문을 다시 저장할 필요가 없습니다.
            동일 입력 READY/EMPTY는200재사용, PENDING/GENERATING은202로 병합하여 추가 호출하지 않습니다.
            진행 중 아닌 목표409 GOAL_NOT_IN_PROGRESS. 없는/삭제 목표404, 타인403.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "동일 입력의 기존 요약 재사용", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202",
                    description = "무료 갱신 대기/생성 중", useReturnTypeSchema = true)
    })
    @PostMapping("/progress-summary/retry")
    public ResponseEntity<ApiResponse<GoalProgressResponses.Summary>> retry(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser user, @PathVariable Long goalId) {
        var result = service.retry(user.userId(), goalId);
        boolean pending = result.status().equals("PENDING") || result.status().equals("GENERATING");
        return ResponseEntity.status(pending ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(ApiResponse.success(result));
    }
}
