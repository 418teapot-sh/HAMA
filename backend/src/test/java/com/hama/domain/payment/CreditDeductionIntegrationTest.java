package com.hama.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hama.domain.goalai.GoalAiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** AI 목표 확정·일정 재계산의 크레딧 차감. 흐름은 GoalAiSelect/ReplanApiIntegrationTest 와 같습니다. */
class CreditDeductionIntegrationTest extends GoalAiTestSupport {

    private int credits(User user) {
        return jdbc.queryForObject("select credits from users where user_id = ?", Integer.class, user.id());
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

    @Test
    void 플랜_선택이_실패하면_쓴_크레딧이_돌아온다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
        int before = credits(user);

        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 409);

        assertThat(credits(user)).isEqualTo(before);
    }

    @Test
    void replan_은_옮긴_게_있으면_1회_쓰고_없으면_돌려준다() throws Exception {
        User user = user();
        long goalId = planningGoal(user);
        long planA = generatePlans(user, goalId).at("/plans/0/planId").asLong();
        call(post("/api/v1/goals/ai/plans/" + planA + "/select"), user, null, 200);
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
