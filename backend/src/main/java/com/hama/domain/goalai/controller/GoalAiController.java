package com.hama.domain.goalai.controller;

import com.hama.domain.goalai.dto.SessionRequests;
import com.hama.domain.goalai.dto.SessionResponses;
import com.hama.domain.goalai.service.GoalAiSessionService;
import com.hama.global.auth.AuthUser;
import com.hama.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표 AI", description = """
        AI 와 대화해 목표를 구체화(세션) → 현실성 체크·초안 수정 → 확정(PLANNING 목표 생성) → A/B 플랜 생성·선택 → 재배치.
        AI 호출이 실패하면 429(AI_RATE_LIMIT) / 502(AI_UPSTREAM_ERROR) / 503(AI_NOT_CONFIGURED) 입니다.
        """)
@RestController
@RequestMapping("/api/v1/goals/ai")
@RequiredArgsConstructor
public class GoalAiController {

    private final GoalAiSessionService sessionService;

    @Operation(summary = "목표 입력 (세션 시작)",
            description = """
                    AI 가 첫 질문을 돌려줍니다. expects 는 다음 답변으로 기대하는 정보(CURRENT_LEVEL | PERIOD | AVAILABLE_TIME | OTHER)입니다.
                    rawGoal 이 비었거나 500자를 넘으면 400(VALIDATION_FAILED).
                    """)
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<SessionResponses.Started>> start(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SessionRequests.Start request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sessionService.start(authUser.userId(), request.rawGoal())));
    }

    @Operation(summary = "AI 질문에 답변",
            description = """
                    정보가 다 모이면 status=READY 와 goalDraft 를, 수집 중이면 status=COLLECTING 과 goalDraft=null 을 돌려줍니다.
                    확정된 세션은 409(AI_SESSION_CONFIRMED), 사용자 메시지가 10개(첫 목표 포함)에 이르면 409(AI_SESSION_TURN_LIMIT).
                    없는 세션 404(AI_SESSION_NOT_FOUND), 다른 사람의 세션 403(FORBIDDEN).
                    """)
    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<SessionResponses.Reply> reply(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId, @Valid @RequestBody SessionRequests.Message request) {
        return ApiResponse.success(sessionService.reply(authUser.userId(), sessionId, request.content()));
    }

    @Operation(summary = "세션·대화 조회",
            description = "대화 전체와 현재 초안·현실성 결과를 돌려줍니다. 404 / 403 은 답변과 같습니다.")
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionResponses.Detail> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(sessionService.get(authUser.userId(), sessionId));
    }
}
