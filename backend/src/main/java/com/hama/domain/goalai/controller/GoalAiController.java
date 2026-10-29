package com.hama.domain.goalai.controller;

import com.hama.domain.goalai.dto.PlanRequests;
import com.hama.domain.goalai.dto.PlanResponses;
import com.hama.domain.goalai.dto.ReplanRequest;
import com.hama.domain.goalai.dto.ReplanResponse;
import com.hama.domain.goalai.dto.ReviewRequests;
import com.hama.domain.goalai.dto.ReviewResponse;
import com.hama.domain.goalai.dto.SelectResponse;
import com.hama.domain.goalai.service.GoalAiPlanService;
import com.hama.domain.goalai.service.GoalAiReplanService;
import com.hama.domain.goalai.service.GoalAiReviewService;
import com.hama.domain.goalai.service.GoalAiSelectService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "목표 AI", description = """
        PLANNING 목표에 A/B 플랜 생성·선택 → 재배치 → 종료 리뷰. 목표 자체는 /api/v1/goals 에서 AI 없이 직접 만듭니다.
        AI 호출이 실패하면 429(AI_RATE_LIMIT) / 502(AI_UPSTREAM_ERROR) / 503(AI_NOT_CONFIGURED) 입니다.
        """)
@RestController
@RequestMapping("/api/v1/goals/ai")
@RequiredArgsConstructor
public class GoalAiController {

    private final GoalAiPlanService planService;
    private final GoalAiSelectService selectService;
    private final GoalAiReplanService replanService;
    private final GoalAiReviewService reviewService;

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
                    플랜대로 투두를 만들고 목표를 IN_PROGRESS 로 바꿉니다.
                    투두는 그날 09:00~22:00 중 고정·개인 일정과 시간이 정해진 기존 투두를 피한 첫 빈 시간(10분 단위)에 둡니다.
                    빈 시간이 없으면 시간 없이 날짜만 두고 placement.unplacedTodos 에 담습니다.
                    없는 플랜 404(AI_PLAN_NOT_FOUND), 다른 사람의 플랜 403, 이미 고른 플랜 409(AI_PLAN_ALREADY_SELECTED),
                    다른 플랜을 이미 골랐으면 409(GOAL_NOT_PLANNING), 목표 기간이 바뀌었거나 플랜 시작일이 지났으면 409(AI_PLAN_OUTDATED).
                    크레딧을 1회 씁니다. 남은 크레딧이 없으면 다른 검사보다 먼저 402(INSUFFICIENT_CREDITS)이고, 실패하면 차감되지 않습니다.
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
                    크레딧을 1회 씁니다. 남은 크레딧이 없으면 다른 검사보다 먼저 402(INSUFFICIENT_CREDITS)이고,
                    실패하거나 바뀐 투두가 없으면(movedCount 0) 차감되지 않습니다.
                    """)
    @PostMapping("/replan")
    public ApiResponse<ReplanResponse> replan(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ReplanRequest request) {
        return ApiResponse.success(replanService.replan(authUser.userId(), request.goalId(), request.fromDate()));
    }

    @Operation(summary = "목표 종료 AI 리뷰 생성",
            description = """
                    END 체크인 뒤 목표 전체(지표·투두 진행률·체크인 기록)를 AI 에 넘겨 달성 여부와 성과 비교·칭찬/격려 멘트를 돌려받습니다.
                    comparison(성과 비교)과 message(칭찬/격려)는 화면에 두 줄로 보여 주도록 각각 줄바꿈 없는 40자 이하 한 줄입니다.
                    achieved 는 AI 가 직접 판정하며 END 체크인의 achieved 는 참고만 합니다. 목표당 리뷰는 1건이고, 입력이 달라 새로 생성하면 덮어씁니다.
                    동일 입력의 저장 결과는 200으로 재사용하며 추가 AI 호출·차감이 없습니다(잔액 0도 재사용 가능).
                    신규 생성은 201이며 크레딧 1개를 쓰고(402 INSUFFICIENT_CREDITS), 실패하면 한 번만 돌려줍니다.
                    생성 중에는 409(AI_REVIEW_GENERATING)이며 추가 호출·차감하지 않습니다.
                    예약은 AI 타임아웃+60초(최소 120초) 후 만료하며 다음 조회/생성 요청에서 회수·환급합니다.
                    아직 같은 토큰을 보유하고 입력이 유효한 응답은 만료만으로 폐기하지 않습니다.
                    이전 리뷰는 입력 지문이 없어 최초 재생성 한 번은 신규 생성으로 처리됩니다.
                    PLANNING 목표는 409(AI_REVIEW_GOAL_NOT_STARTED), 완료(COMPLETED)하지 않았고 종료일도 지나지 않았으면 409(AI_REVIEW_GOAL_NOT_ENDED),
                    END 체크인이 없으면 409(AI_REVIEW_END_REQUIRED), AI 응답을 기다리는 동안 목표·체크인이 바뀌면 409(AI_REVIEW_OUTDATED).
                    없는 목표 404(GOAL_NOT_FOUND), 다른 사람의 목표 403(FORBIDDEN).
                    """)
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "동일 입력의 저장 결과 재사용·추가 차감 없음", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "새 리뷰 생성·크레딧 1회 차감", useReturnTypeSchema = true)
    })
    @PostMapping("/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ReviewRequests.Create request) {
        var result = reviewService.create(authUser.userId(), request.goalId(), request.currentStatus());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.response()));
    }

    @Operation(summary = "목표 종료 AI 리뷰 조회",
            description = """
                    가장 최근에 만든 리뷰를 돌려줍니다. 만료된 생성 예약이 있으면 차감분을 한 번 환급합니다.
                    이후 리뷰 없음404(AI_REVIEW_NOT_FOUND)·삭제 목표404여도 환급은 보존됩니다. 403은 생성과 같습니다.
                    조회/생성 요청이 없을 때의 백그라운드 환급은 보장하지 않습니다. 잔액은 내 정보 API로 다시 확인하세요.
                    """)
    @GetMapping("/reviews")
    public ApiResponse<ReviewResponse> review(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Long goalId) {
        return ApiResponse.success(reviewService.get(authUser.userId(), goalId));
    }
}
