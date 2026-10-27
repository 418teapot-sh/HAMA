package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class GoalAiReviewApiIntegrationTest extends GoalAiTestSupport {

    private static final String ACHIEVED_ANSWER = """
            {"achieved":true,"comparison":"700점에서 860점으로, 목표 850점을 넘었어요.",
             "message":"목표를 달성했어요! 정말 잘하셨어요."}
            """;
    private static final String MISSED_ANSWER = """
            {"achieved":false,"comparison":"700점에서 780점으로, 목표 850점에는 못 미쳤어요.",
             "message":"그래도 80점이나 올랐어요. 다음엔 더 잘할 수 있어요."}
            """;

    /** 플랜을 골라 IN_PROGRESS 로 만든 목표(2026-10-01 ~ 2026-12-31, 아직 안 끝남). */
    private long inProgressGoal(User user) throws Exception {
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        return goalId;
    }

    /** 오늘(2026-10-01) 이전으로 종료일을 당겨, 리뷰 가능한(effectiveStatus=PAST) 상태로 만듭니다. */
    private void endGoal(long goalId) {
        jdbc.update("update goal set end_date = '2026-09-30' where goal_id = ?", goalId);
    }

    private void checkin(User user, long goalId, String type, Object value, Boolean achieved) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("goalId", goalId, "type", type, "value", value));
        if (achieved != null) {
            body.put("achieved", achieved);
        }
        call(post("/api/v1/todos/state/checkins"), user, body, 201);
    }

    private int credits(User user) {
        return jdbc.queryForObject("select credits from users where user_id = ?", Integer.class, user.id());
    }

    @Test
    void END_체크인_뒤_리뷰를_만들면_AI_가_판정한_결과를_저장하고_크레딧을_쓴다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        endGoal(goalId);
        checkin(user, goalId, "START", 700, null);
        checkin(user, goalId, "MID", 780, null);
        checkin(user, goalId, "END", 860, true);
        int before = credits(user);

        AI.answer(ACHIEVED_ANSWER);
        JsonNode data = call(post("/api/v1/goals/ai/reviews"), user,
                Map.of("goalId", goalId, "currentStatus", "토익 860점 획득"), 201).get("data");

        assertThat(data.get("goalId").asLong()).isEqualTo(goalId);
        assertThat(data.get("achieved").asBoolean()).isTrue();
        assertThat(data.get("comparison").asString()).contains("860");
        assertThat(data.get("message").asString()).isNotBlank();
        assertThat(data.get("currentStatus").asString()).isEqualTo("토익 860점 획득");
        assertThat(data.get("resultStatus").asString()).isEqualTo("ACHIEVED");

        assertThat(jdbc.queryForObject("select result_status from goal where goal_id = ?", String.class, goalId))
                .isEqualTo("ACHIEVED");
        assertThat(jdbc.queryForObject("select count(*) from goal_review where goal_id = ?", Integer.class, goalId))
                .isEqualTo(1);
        assertThat(credits(user)).isEqualTo(before - 1);

        assertThat(AI.requests().getLast().purpose()).isEqualTo("goal-review");
        assertThat(AI.requests().getLast().messages().getFirst().content())
                .contains("토익 860점 획득").contains("\"progressRate\"");
    }

    @Test
    void 다시_리뷰하면_기존_행을_덮어쓴다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        endGoal(goalId);
        checkin(user, goalId, "END", 860, true);

        AI.answer(ACHIEVED_ANSWER);
        call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 201);

        AI.answer(MISSED_ANSWER);
        JsonNode data = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 201).get("data");

        assertThat(data.get("achieved").asBoolean()).isFalse();
        assertThat(data.get("resultStatus").asString()).isEqualTo("MISSED");
        assertThat(jdbc.queryForObject("select count(*) from goal_review where goal_id = ?", Integer.class, goalId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select result_status from goal where goal_id = ?", String.class, goalId))
                .isEqualTo("MISSED");
    }

    @Test
    void 계획_중이거나_종료_전이거나_END_체크인이_없으면_409_남의_목표는_403_없는_목표는_404() throws Exception {
        User user = user();
        long planning = planningGoal(user);
        JsonNode notStarted = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", planning), 409);
        assertThat(notStarted.at("/error/code").asString()).isEqualTo("AI_REVIEW_GOAL_NOT_STARTED");

        long planA = generatePlans(user, planning).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        int before = credits(user);
        JsonNode notEnded = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", planning), 409);
        assertThat(notEnded.at("/error/code").asString()).isEqualTo("AI_REVIEW_GOAL_NOT_ENDED");

        endGoal(planning);
        JsonNode noEnd = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", planning), 409);
        assertThat(noEnd.at("/error/code").asString()).isEqualTo("AI_REVIEW_END_REQUIRED");

        checkin(user, planning, "START", 700, null);
        JsonNode stillNoEnd = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", planning), 409);
        assertThat(stillNoEnd.at("/error/code").asString()).isEqualTo("AI_REVIEW_END_REQUIRED");

        call(post("/api/v1/goals/ai/reviews"), user(), Map.of("goalId", planning), 403);
        call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", 999999999), 404);

        // 위 실패들은 모두 자격 검증 단계에서 막혀 크레딧을 쓰지 않습니다.
        assertThat(credits(user)).isEqualTo(before);
    }

    @Test
    void 리뷰_조회는_만든_결과를_돌려주고_없으면_404() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        endGoal(goalId);
        checkin(user, goalId, "END", 860, true);

        JsonNode missing = call(get("/api/v1/goals/ai/reviews").param("goalId", String.valueOf(goalId)), user, null,
                404);
        assertThat(missing.at("/error/code").asString()).isEqualTo("AI_REVIEW_NOT_FOUND");

        AI.answer(ACHIEVED_ANSWER);
        call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 201);

        JsonNode data = call(get("/api/v1/goals/ai/reviews").param("goalId", String.valueOf(goalId)), user, null,
                200).get("data");
        assertThat(data.get("achieved").asBoolean()).isTrue();
        assertThat(data.get("resultStatus").asString()).isEqualTo("ACHIEVED");

        call(get("/api/v1/goals/ai/reviews").param("goalId", String.valueOf(goalId)), user(), null, 403);
    }

    @Test
    void AI_가_답하는_동안_목표가_바뀌면_409_하고_크레딧을_돌려준다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        endGoal(goalId);
        checkin(user, goalId, "END", 860, true);
        int before = credits(user);

        AI.onNextAnswer(() -> jdbc.update("update goal set target_value = '950.00' where goal_id = ?", goalId));
        AI.answer(ACHIEVED_ANSWER);
        JsonNode error = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 409);

        assertThat(error.at("/error/code").asString()).isEqualTo("AI_REVIEW_OUTDATED");
        assertThat(credits(user)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from goal_review where goal_id = ?", Integer.class, goalId))
                .isZero();
        assertThat(jdbc.queryForObject("select result_status from goal where goal_id = ?", String.class, goalId))
                .isNull();
    }

    @Test
    void 리뷰_도중_다른_요청이_먼저_저장하면_409_하고_크레딧을_돌려준다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        endGoal(goalId);
        checkin(user, goalId, "END", 860, true);
        int before = credits(user);

        // 목표/체크인은 그대로지만, AI 가 답하기 직전에 다른 요청이 리뷰를 먼저 저장해버린 상황을 흉내냅니다.
        AI.onNextAnswer(() -> jdbc.update(
                "insert into goal_review (goal_id, achieved, comparison, message, current_status, created_at, updated_at) "
                        + "values (?, true, '먼저 저장됨', '먼저 저장됨', '먼저 저장됨', now(), now())",
                goalId));
        AI.answer(ACHIEVED_ANSWER);
        JsonNode error = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 409);

        assertThat(error.at("/error/code").asString()).isEqualTo("AI_REVIEW_OUTDATED");
        assertThat(credits(user)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from goal_review where goal_id = ?", Integer.class, goalId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select comparison from goal_review where goal_id = ?", String.class, goalId))
                .isEqualTo("먼저 저장됨");
        assertThat(jdbc.queryForObject("select result_status from goal where goal_id = ?", String.class, goalId))
                .isNull();
    }

    @Test
    void 크레딧이_없으면_402_이고_AI를_부르지_않는다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        endGoal(goalId);
        checkin(user, goalId, "END", 860, true);
        jdbc.update("update users set credits = 0 where user_id = ?", user.id());

        call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 402);

        // inProgressGoal() 설정 과정(세션 대화·플랜 생성)에서도 AI 를 부르므로, 전체가 아니라 리뷰 호출만 확인합니다.
        assertThat(AI.requests().stream().filter(r -> r.purpose().equals("goal-review"))).isEmpty();
    }

    @Test
    void AI_응답이_올바르지_않으면_502_하고_크레딧을_돌려준다() throws Exception {
        User user = user();
        long goalId = inProgressGoal(user);
        endGoal(goalId);
        checkin(user, goalId, "END", 860, true);
        int before = credits(user);

        AI.answer("{}");
        JsonNode error = call(post("/api/v1/goals/ai/reviews"), user, Map.of("goalId", goalId), 502);

        assertThat(error.at("/error/code").asString()).isEqualTo("AI_UPSTREAM_ERROR");
        assertThat(credits(user)).isEqualTo(before);
    }
}
