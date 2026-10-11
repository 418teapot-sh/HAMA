package com.hama.domain.goalai.controller;

import com.hama.domain.goalai.dto.DraftResponses;
import com.hama.domain.goalai.dto.PlanRequests;
import com.hama.domain.goalai.dto.PlanResponses;
import com.hama.domain.goalai.dto.RealityResult;
import com.hama.domain.goalai.dto.ReplanRequest;
import com.hama.domain.goalai.dto.ReplanResponse;
import com.hama.domain.goalai.dto.SelectResponse;
import com.hama.domain.goalai.dto.SessionRequests;
import com.hama.domain.goalai.dto.SessionResponses;
import com.hama.domain.goalai.dto.UpdateGoalDraftRequest;
import com.hama.domain.goalai.service.GoalAiDraftService;
import com.hama.domain.goalai.service.GoalAiPlanService;
import com.hama.domain.goalai.service.GoalAiReplanService;
import com.hama.domain.goalai.service.GoalAiSelectService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final GoalAiDraftService draftService;
    private final GoalAiPlanService planService;
    private final GoalAiSelectService selectService;
    private final GoalAiReplanService replanService;

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

    @Operation(summary = "현실성 체크",
            description = """
                    초안의 달성 가능성을 평가합니다. availableHours 는 기간 일수 / 7 × 주간 가용시간, gapHours 는 max(0, 필요 - 가용)입니다.
                    주간 가용시간이 없으면 availableHours·gapHours 는 null 입니다.
                    suggestions[].patch 는 초안 수정(PATCH draft) 본문으로 그대로 보낼 수 있습니다.
                    초안이 아직 없으면 409(AI_SESSION_NOT_READY), 확정된 세션은 409(AI_SESSION_CONFIRMED).
                    """)
    @PostMapping("/sessions/{sessionId}/reality-check")
    public ApiResponse<RealityResult> realityCheck(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(draftService.realityCheck(authUser.userId(), sessionId));
    }

    @Operation(summary = "초안 수정",
            description = """
                    바꿀 필드만 보냅니다. 수정하면 이전 현실성 결과는 지워집니다(세션 조회의 realityResult=null).
                    기간이 잘못됐으면 400(GOAL_INVALID_PERIOD), 365일(시작일 포함)을 넘으면 400(GOAL_PERIOD_TOO_LONG), 409 는 현실성 체크와 같습니다.
                    """)
    @PatchMapping("/sessions/{sessionId}/draft")
    public ApiResponse<DraftResponses.DraftView> updateDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId, @Valid @RequestBody UpdateGoalDraftRequest request) {
        return ApiResponse.success(draftService.updateDraft(authUser.userId(), sessionId, request));
    }

    @Operation(summary = "목표 확정",
            description = """
                    초안으로 PLANNING 목표를 만듭니다. 현실성 체크를 했다면 그 판정·코멘트도 함께 저장합니다.
                    확정 사이 종료일이 지났으면 400(GOAL_INVALID_PERIOD), 기간이 365일(시작일 포함)을 넘으면 400(GOAL_PERIOD_TOO_LONG),
                    409 는 현실성 체크와 같습니다.
                    """)
    @PostMapping("/sessions/{sessionId}/confirm")
    public ResponseEntity<ApiResponse<DraftResponses.Confirmed>> confirm(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(draftService.confirm(authUser.userId(), sessionId)));
    }

    @Operation(summary = "A/B 플랜 생성",
            description = """
                    PLANNING 목표에 A(여유형)·B(집중형) 플랜을 만듭니다. 다시 만들면 선택하지 않은 기존 플랜을 바꿉니다.
                    투두는 목표 시작일과 오늘 중 늦은 날부터 종료일까지 둡니다. preference 는 RELAXED | BALANCED | INTENSIVE(생략 시 BALANCED).
                    없는 목표 404(GOAL_NOT_FOUND), 다른 사람의 목표 403, PLANNING 이 아니면 409(GOAL_NOT_PLANNING),
                    기간이 없거나 이미 끝났으면 400(GOAL_INVALID_PERIOD), 만드는 사이 목표 기간이 바뀌면 409(AI_PLAN_OUTDATED).
                    """)
    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<PlanResponses.PlanList>> generatePlans(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PlanRequests.Generate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                planService.generate(authUser.userId(), request.goalId(), request.preference())));
    }

    @Operation(summary = "플랜 조회",
            description = "목표의 플랜 목록과 선택 여부(selected)를 돌려줍니다. 플랜이 없으면 빈 배열입니다. 404 / 403 은 생성과 같습니다.")
    @GetMapping("/plans")
    public ApiResponse<PlanResponses.PlanList> plans(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Long goalId) {
        return ApiResponse.success(planService.list(authUser.userId(), goalId));
    }

    @Operation(summary = "플랜 선택",
            description = """
                    플랜대로 마일스톤·주간 목표·투두를 만들고 목표를 IN_PROGRESS 로 바꿉니다.
                    투두는 그날 09:00~22:00 중 고정·개인 일정과 시간이 정해진 기존 투두를 피한 첫 빈 시간(10분 단위)에 둡니다.
                    빈 시간이 없으면 시간 없이 날짜만 두고 placement.unplacedTodos 에 담습니다.
                    없는 플랜 404(AI_PLAN_NOT_FOUND), 다른 사람의 플랜 403, 이미 고른 플랜 409(AI_PLAN_ALREADY_SELECTED),
                    다른 플랜을 이미 골랐으면 409(GOAL_NOT_PLANNING), 목표 기간이 바뀌었거나 플랜 시작일이 지났으면 409(AI_PLAN_OUTDATED).
                    """)
    @PostMapping("/plans/{planId}/select")
    public ApiResponse<SelectResponse> selectPlan(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long planId) {
        return ApiResponse.success(selectService.select(authUser.userId(), planId));
    }

    @Operation(summary = "AI 투두 재배치",
            description = """
                    목표의 미완료 AI 투두(AI_GOAL_TASK)만 옮깁니다. fromDate 이후에 정한 시간이 비어 있는 투두는 그대로 두고,
                    밀린 투두·시간 없는 투두·일정과 겹친 투두를 fromDate(밀린 투두) 또는 원래 날짜부터 목표 종료일까지 첫 빈 시간에 둡니다.
                    빈 시간이 끝까지 없으면 시간 없이 날짜만 둡니다(unplacedCount). 미룬 횟수는 늘지 않습니다.
                    fromDate 를 생략하면 오늘(KST)과 목표 시작일 중 늦은 날입니다.
                    진행 중이 아닌 목표 409(GOAL_NOT_IN_PROGRESS), fromDate 가 오늘 이전이거나 목표 기간 밖이면 400(AI_REPLAN_INVALID_DATE).
                    """)
    @PostMapping("/replan")
    public ApiResponse<ReplanResponse> replan(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ReplanRequest request) {
        return ApiResponse.success(replanService.replan(authUser.userId(), request.goalId(), request.fromDate()));
    }
}
