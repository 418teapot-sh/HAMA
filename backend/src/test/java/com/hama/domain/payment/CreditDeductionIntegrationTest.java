package com.hama.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hama.domain.goalai.GoalAiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/** AI 목표 확정·일정 재계산의 크레딧 차감. 흐름은 GoalAiSelect/ReplanApiIntegrationTest 와 같습니다. */
class CreditDeductionIntegrationTest extends GoalAiTestSupport {

    private Integer credits(User user) {
        Integer credits = jdbc.queryForObject("select credits from users where user_id = ?", Integer.class, user.id());
        assertThat(credits).as("users.credits of %d", user.id()).isNotNull();
        return credits;
    }

    private void setCredits(User user, int credits) {
        jdbc.update("update users set credits = ? where user_id = ?", credits, user.id());
    }

    @Test
    void 플랜을_고르면_1회_쓴다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        setCredits(user, 1);

        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);

        assertThat(credits(user)).isZero();
    }

    @Test
    void 크레딧이_없으면_402_이고_목표를_만들지_않는다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        setCredits(user, 0);

        JsonNode error = call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 402);

        assertThat(error.at("/error/code").asString()).isEqualTo("INSUFFICIENT_CREDITS");
        assertThat(jdbc.queryForObject("select status from goal where goal_id = ?", String.class, goalId))
                .isEqualTo("PLANNING");
        assertThat(jdbc.queryForObject("select count(*) from todo where goal_id = ?", Integer.class, goalId))
                .isZero();
    }

    /** credits.use 뒤에서 던지는 예외마다 롤백으로 크레딧이 돌아오는지 봅니다. */
    @ParameterizedTest
    @ValueSource(strings = {"AI_PLAN_ALREADY_SELECTED", "GOAL_NOT_PLANNING", "AI_PLAN_OUTDATED", "FORBIDDEN",
            "AI_PLAN_NOT_FOUND"})
    void 플랜_선택이_실패하면_쓴_크레딧이_돌아온다(String code) throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        JsonNode plans = generatePlans(user, goalId).get("plans");
        long planA = plans.at("/0/planId").asLong();
        User caller = user;
        long target = planA;
        int status = 409;
        switch (code) {
            case "AI_PLAN_ALREADY_SELECTED" -> call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
            case "GOAL_NOT_PLANNING" -> {
                call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
                target = plans.at("/1/planId").asLong();
            }
            case "AI_PLAN_OUTDATED" -> jdbc.update("update goal set end_date = '2027-01-31' where goal_id = ?", goalId);
            case "FORBIDDEN" -> {
                caller = user();
                status = 403;
            }
            case "AI_PLAN_NOT_FOUND" -> {
                target = 999999999L;
                status = 404;
            }
            default -> throw new IllegalArgumentException(code);
        }
        int before = credits(caller);

        JsonNode error = call(post("/api/v1/goals/ai/plans/" + target + "/select"), caller, null, status);

        assertThat(error.at("/error/code").asString()).isEqualTo(code);
        assertThat(credits(caller)).isEqualTo(before);
    }

    @Test
    void replan_은_옮긴_게_있으면_1회_쓰고_없으면_돌려준다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        // 오늘(GoalAiTestSupport 고정 시계, 2026-10-01) 이후 투두 하나를 어제로 밀어 둡니다.
        long todoId = jdbc.queryForObject(
                "select min(todo_id) from todo where goal_id = ? and todo_date > '2026-10-01'", Long.class, goalId);
        jdbc.update("update todo set todo_date = '2026-09-30' where todo_id = ?", todoId);
        setCredits(user, 1);

        JsonNode moved = call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", goalId), 200).get("data");
        assertThat(moved.get("movedCount").asInt()).isEqualTo(1);
        assertThat(credits(user)).isZero();

        // 남은 AI 투두를 다 끝내서 옮길 게 없게 만듭니다.
        jdbc.update("update todo set status = 'COMPLETED' where goal_id = ?", goalId);
        setCredits(user, 1);
        JsonNode none = call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", goalId), 200).get("data");
        assertThat(none.get("movedCount").asInt()).isZero();
        assertThat(none.get("unplacedCount").asInt()).isZero();
        assertThat(credits(user)).isEqualTo(1);
    }

    @Test
    void replan_은_빈_시간이_없어_날짜만_옮겨도_1회_쓴다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        long todoId = jdbc.queryForObject(
                "select min(todo_id) from todo where goal_id = ? and todo_date > '2026-10-01'", Long.class, goalId);
        jdbc.update("update todo set todo_date = '2026-09-30' where todo_id = ?", todoId);
        // 목표 기간 내내 종일 일정으로 막아 빈 시간이 없게 합니다.
        call(post("/api/v1/calendar/schedules"), user, Map.of("type", "PERSONAL", "title", "출장",
                "startAt", "2026-10-01T00:00:00", "endAt", "2027-01-01T00:00:00", "allDay", true), 201);
        setCredits(user, 1);

        JsonNode data = call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", goalId), 200).get("data");

        assertThat(data.get("movedCount").asInt()).isPositive();
        assertThat(data.get("unplacedCount").asInt()).isPositive();
        assertThat(credits(user)).isZero();
    }

    @Test
    void replan_도_크레딧이_없으면_402() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        setCredits(user, 0);

        JsonNode error = call(post("/api/v1/goals/ai/replan"), user, Map.of("goalId", goalId), 402);

        assertThat(error.at("/error/code").asString()).isEqualTo("INSUFFICIENT_CREDITS");
    }
}
