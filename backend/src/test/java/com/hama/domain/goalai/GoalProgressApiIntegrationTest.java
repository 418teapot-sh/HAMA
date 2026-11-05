package com.hama.domain.goalai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hama.domain.goalai.service.GoalProgressService;
import com.hama.domain.todo.dto.UpdateTodoNoteRequest;
import com.hama.domain.todo.service.TodoService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class GoalProgressApiIntegrationTest extends GoalAiTestSupport {

    private static final ProgressClock TIME = new ProgressClock();

    @org.springframework.test.context.bean.override.convention.TestBean(name = "be3Clock", methodName = "progressClock")
    protected Clock clock;

    static Clock progressClock() { return TIME; }

    private static final class ProgressClock extends Clock {
        private volatile Instant instant = Instant.parse("2026-10-01T03:00:00Z");
        void advance(long seconds) { instant = instant.plusSeconds(seconds); }
        void reset() { instant = Instant.parse("2026-10-01T03:00:00Z"); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }

    @Autowired
    private GoalProgressService worker;
    @Autowired
    private TodoService todoService;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void isolateDurableQueue() {
        TIME.reset();
        // 워커는 전역 DB 대기를 읽으므로 이전 테스트의 작업을 hama_test에서 격리합니다.
        jdbc.update("delete from goal_progress_summary");
    }

    /** 요약 대상인 진행 중 목표를 만들고, 기록 투두 완료로 자동 종료되지 않도록 미완료 투두를 남깁니다. */
    private long goal(User user) throws Exception {
        long id = call(post("/api/v1/goals"), user, Map.of("title", "토익 850점", "startDate", "2026-10-01",
                "endDate", "2026-10-31"), 201).at("/data/goalId").asLong();
        jdbc.update("update goal set status = 'IN_PROGRESS' where goal_id = ?", id);
        todo(user, id);
        return id;
    }

    private long todo(User user, long goalId) throws Exception {
        return call(post("/api/v1/todos"), user, Map.of("category", "AI_GOAL_TASK", "goalId", goalId,
                "content", "모의고사", "todoDate", "2026-10-01"), 201).at("/data/todoId").asLong();
    }

    private long record(User user, long goalId, String text) throws Exception {
        long id = todo(user, goalId);
        call(patch("/api/v1/todos/" + id + "/complete"), user, null, 200);
        note(user, id, text);
        return id;
    }

    private void note(User user, long id, String text) throws Exception {
        call(patch("/api/v1/todos/state/" + id), user, Map.of("statusNote", text), 200);
    }

    private JsonNode summary(User user, long goalId) throws Exception {
        return call(get("/api/v1/goals/" + goalId + "/progress-summary"), user, null, 200).get("data");
    }

    private JsonNode records(User user, long goalId) throws Exception {
        return call(get("/api/v1/goals/" + goalId + "/progress-records"), user, null, 200).get("data");
    }

    private long calls() {
        return AI.requests().stream().filter(r -> r.purpose().equals("goal-progress-summary")).count();
    }

    private void answer(String text) {
        AI.answer("{\"summary\":\"" + text + "\"}");
    }

    @Test
    void 완료취소는_완료율과_요약을_갱신하고_상태원문은_재완료까지_보존한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        answer("처음 요약");
        worker.process(goalId);
        String previousAt = records(user, goalId).at("/content/0/completedAt").asString();
        var result = call(patch("/api/v1/todos/" + id + "/uncomplete"), user, null, 200);
        assertThat(result.at("/data/goalProgressRate").asDouble()).isZero();
        assertThat(records(user, goalId).get("totalElements").asLong()).isZero();
        var empty = summary(user, goalId);
        assertThat(empty.get("status").asString()).isEqualTo("EMPTY");
        assertThat(empty.get("summary").isNull()).isTrue();
        TIME.advance(60);
        var completed = call(patch("/api/v1/todos/" + id + "/complete"), user, null, 200);
        assertThat(completed.at("/data/goalProgressRate").asDouble()).isEqualTo(50.0);
        var record = records(user, goalId).at("/content/0");
        assertThat(record.get("statusNote").asString()).isEqualTo("700점");
        assertThat(record.get("completedAt").asString()).isNotEqualTo(previousAt);
        answer("재완료 요약");
        worker.process(goalId);
        assertThat(summary(user, goalId).get("summary").asString()).isEqualTo("재완료 요약");
    }

    @Test
    void 이벤트_없는_원본_변경도_GET이_감지하되_호출_간격을_보존한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        answer("처음 요약");
        worker.process(goalId);
        jdbc.update("update goal set current_status = '새 현재 상태' where goal_id = ?", goalId);
        var state = summary(user, goalId);
        assertThat(state.get("stale").asBoolean()).isTrue();
        assertThat(state.get("status").asString()).isEqualTo("PENDING");
        worker.process(goalId);
        assertThat(calls()).isEqualTo(1);
        TIME.advance(60);
        answer("변경된 상태 요약");
        worker.process(goalId);
        assertThat(AI.requests().getLast().messages().getFirst().content()).contains("새 현재 상태");
        assertThat(summary(user, goalId).get("stale").asBoolean()).isFalse();
    }

    @Test
    void 수정과_명시적_재시도는_영속된_60초_간격을_우회하지_못한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        answer("첫 요약");
        worker.process(goalId);
        TIME.advance(59);
        note(user, id, "750점");
        note(user, id, "800점");
        var pending = call(post("/api/v1/goals/" + goalId + "/progress-summary/retry"), user, null, 202);
        assertThat(pending.at("/data/nextAttemptAt").asString()).isEqualTo("2026-10-01T12:01:00");
        worker.processDue();
        assertThat(calls()).isEqualTo(1);
        TIME.advance(1);
        answer("최신 800점 요약");
        worker.processDue();
        assertThat(calls()).isEqualTo(2);
        assertThat(AI.requests().getLast().messages().getFirst().content()).contains("800점");
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("READY");
    }

    @Test
    void 미회수_토큰의_느린_성공은_만료만으로_버리거나_재시도하지_않는다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        AI.onNextAnswer(() -> TIME.advance(121));
        answer("느리지만 유효한 요약");
        worker.process(goalId);
        worker.processDue();
        assertThat(summary(user, goalId).get("summary").asString()).isEqualTo("느리지만 유효한 요약");
        assertThat(calls()).isEqualTo(1);
    }

    @Test
    void 만료만_반복해도_같은_입력은_세_번_후_멈춘다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        for (int i = 0; i < 3; i++) {
            AI.onNextAnswer(() -> TIME.advance(121));
            AI.answer("{}");
            worker.process(goalId);
            TIME.advance(60);
        }
        assertThat(calls()).isEqualTo(3);
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("FAILED");
        worker.processDue();
        assertThat(calls()).isEqualTo(3);
    }

    @Test
    void 재시도는_만료_예약을_실제_대기로_회수하고_시도_횟수를_보존한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        jdbc.update("""
                update goal_progress_summary set request_token = 'crash', lease_until = '2026-09-30',
                    status = 'GENERATING', attempts = 2 where goal_id = ?
                """, goalId);
        var response = call(post("/api/v1/goals/" + goalId + "/progress-summary/retry"), user, null, 202);
        assertThat(response.at("/data/status").asString()).isEqualTo("PENDING");
        assertThat(response.at("/data/attempts").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select request_token from goal_progress_summary where goal_id = ?",
                String.class, goalId)).isNull();
        answer("남은 한 번으로 복구");
        worker.process(goalId);
        assertThat(calls()).isEqualTo(1);
    }

    @Test
    void 일반_폴링과_타인_재시도는_다른_트랜잭션의_목표_잠금을_기다리지_않는다() throws Exception {
        User user = user();
        User other = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        answer("저장 요약");
        worker.process(goalId);
        var before = jdbc.queryForMap("select revision, updated_at, next_attempt_at from goal_progress_summary where goal_id = ?", goalId);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> transaction.executeWithoutResult(tx -> {
                jdbc.queryForList("select goal_id from goal where goal_id = ? for update", goalId);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lock test timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                executor.submit(() -> {
                    summary(user, goalId);
                    summary(user, goalId);
                    call(post("/api/v1/goals/" + goalId + "/progress-summary/retry"), other, null, 403);
                    return null;
                }).get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForMap("select revision, updated_at, next_attempt_at from goal_progress_summary where goal_id = ?", goalId))
                .isEqualTo(before);
    }

    @Test
    void 두_워커가_겹쳐도_AI는_한_번만_호출하고_대기_중_DB잠금을_보유하지_않는다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        AI.onNextAnswer(() -> {
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> worker.process(goalId)).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        answer("중복 없는 요약");
        worker.process(goalId);
        assertThat(calls()).isEqualTo(1);
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("READY");
        assertThat(jdbc.queryForObject("select credits from users where user_id = ?", Integer.class, user.id()))
                .isEqualTo(DEFAULT_TEST_CREDITS);
    }

    @Test
    void 상태_기록은_수정_시각이_아닌_실제_완료_시각으로_정렬한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long newest = record(user, goalId, "최근 완료");
        long earlier = record(user, goalId, "이전 완료");
        // 실제 완료 API도 JVM 시간대와 무관하게 고정 KST 시각(12:00)을 왕복해야 합니다.
        assertThat(records(user, goalId).at("/content/0/completedAt").asString()).isEqualTo("2026-10-01T12:00:00");
        // DATETIME 문자열 직접 주입은 UTC JVM에서 JDBC 시간대 변환 경로가 달라집니다.
        // 실제 complete()와 같은 Hibernate LocalDateTime 바인딩으로 기록합니다.
        transaction.executeWithoutResult(tx -> entityManager.createQuery(
                "update Todo t set t.completedAt = :at where t.id = :id")
                .setParameter("at", LocalDateTime.of(2026, 9, 30, 23, 59, 59))
                .setParameter("id", earlier).executeUpdate());
        note(user, earlier, "지금 수정한 이전 완료 기록");
        JsonNode result = records(user, goalId);
        assertThat(result.at("/content/0/todoId").asLong()).isEqualTo(newest);
        assertThat(result.at("/content/1/completedAt").asString()).isEqualTo("2026-09-30T23:59:59");
    }

    @Test
    void 완료_투두_상태_저장_후_DB_대기에서_자동_요약하고_크레딧과_달성판정은_건드리지_않는다() throws Exception {
        User user = user();
        long goalId = goal(user);
        String original = "가".repeat(400);
        long todoId = record(user, goalId, original);
        jdbc.update("update users set credits = 0 where user_id = ?", user.id());
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("PENDING");
        answer("현재 상태를 기록했어요.");
        worker.processDue(); // 메모리 이벤트가 없어도 저장된 대기 작업을 처리합니다.

        JsonNode result = summary(user, goalId);
        assertThat(result.get("status").asString()).isEqualTo("READY");
        assertThat(result.get("stale").asBoolean()).isFalse();
        assertThat(result.get("usedRecordCount").asInt()).isEqualTo(1);
        assertThat(result.get("sourceRecordCount").asLong()).isEqualTo(1);
        assertThat(records(user, goalId).at("/content/0/statusNote").asString()).isEqualTo(original);
        assertThat(records(user, goalId).at("/content/0/todoId").asLong()).isEqualTo(todoId);
        assertThat(jdbc.queryForObject("select credits from users where user_id = ?", Integer.class, user.id()))
                .isZero();
        assertThat(jdbc.queryForObject("select result_status from goal where goal_id = ?", String.class, goalId))
                .isNull();
        JsonNode input = json.readTree(AI.requests().getLast().messages().getFirst().content());
        assertThat(input.at("/records/0/statusNote").asString()).hasSize(300);
        assertThat(input.get("progressRate").decimalValue()).isEqualByComparingTo("50.0");
        summary(user, goalId);
        worker.process(goalId);
        assertThat(calls()).isEqualTo(1);
    }

    @Test
    void 여러_번_수정해도_투두별_한_기록이며_최신_입력_한_번만_요약한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        note(user, id, "750점");
        note(user, id, "800점");
        String completedAt = records(user, goalId).at("/content/0/completedAt").asString();
        long revision = jdbc.queryForObject("select revision from goal_progress_summary where goal_id = ?", Long.class,
                goalId);
        note(user, id, "800점");
        assertThat(jdbc.queryForObject("select revision from goal_progress_summary where goal_id = ?", Long.class,
                goalId)).isEqualTo(revision);
        answer("800점까지 기록했어요.");
        worker.process(goalId);
        assertThat(calls()).isEqualTo(1);
        assertThat(records(user, goalId).get("totalElements").asLong()).isEqualTo(1);
        assertThat(records(user, goalId).at("/content/0/completedAt").asString()).isEqualTo(completedAt);
        assertThat(AI.requests().getLast().messages().getFirst().content()).contains("800점").doesNotContain("700점");
    }

    @Test
    void AI_응답_중_새_메모가_저장되면_옛_요약은_버리고_최신_작업을_남긴다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        AI.onNextAnswer(() -> {
            try {
                note(user, id, "800점");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        answer("옛 요약");
        worker.process(goalId);
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("PENDING");
        assertThat(summary(user, goalId).get("summary").isNull()).isTrue();
        TIME.advance(60);
        answer("최신 요약");
        worker.process(goalId);
        assertThat(summary(user, goalId).get("summary").asString()).isEqualTo("최신 요약");
    }

    @Test
    void 실패는_원문과_이전_요약을_보존하고_세번_후_명시적_재시도한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        answer("이전 요약");
        worker.process(goalId);
        note(user, id, "800점");
        for (int i = 0; i < 3; i++) {
            TIME.advance(60);
            AI.answer("{}");
            worker.process(goalId);
        }
        JsonNode failed = summary(user, goalId);
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("summary").asString()).isEqualTo("이전 요약");
        assertThat(failed.get("stale").asBoolean()).isTrue();
        assertThat(records(user, goalId).at("/content/0/statusNote").asString()).isEqualTo("800점");
        long before = calls();
        worker.processDue();
        assertThat(calls()).isEqualTo(before);
        call(post("/api/v1/goals/" + goalId + "/progress-summary/retry"), user, null, 202);
        TIME.advance(60);
        answer("복구 요약");
        worker.process(goalId);
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("READY");
    }

    @Test
    void 예약_중복_처리는_AI를_부르지_않고_만료_후_복구한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        jdbc.update("""
                update goal_progress_summary set request_token = 'crash', lease_until = '2026-10-02',
                    status = 'GENERATING', attempts = 1 where goal_id = ?
                """, goalId);
        worker.process(goalId);
        assertThat(calls()).isZero();
        jdbc.update("update goal_progress_summary set lease_until = '2026-09-30' where goal_id = ?", goalId);
        answer("재시작 후 복구");
        worker.processDue();
        assertThat(summary(user, goalId).get("summary").asString()).isEqualTo("재시작 후 복구");
        assertThat(calls()).isEqualTo(1);
    }

    @Test
    void 마지막_시도_중_중단된_예약도_만료_후_FAILED로_종결한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        jdbc.update("""
                update goal_progress_summary set request_token = 'crash', lease_until = '2026-09-30',
                    status = 'GENERATING', attempts = 3 where goal_id = ?
                """, goalId);
        worker.processDue();
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("FAILED");
        assertThat(calls()).isZero();
    }

    @Test
    void 만료_후_새_토큰이_성공하면_늦은_이전_결과는_덮어쓰지_않는다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        AI.onNextAnswer(() -> {
            TIME.advance(120);
            worker.process(goalId);
        });
        answer("새 토큰 결과");
        answer("늦은 이전 결과");
        worker.process(goalId);
        assertThat(summary(user, goalId).get("summary").asString()).isEqualTo("새 토큰 결과");
    }

    @Test
    void 종료_후_상태_수정은_보존하되_AI_생성은_멈추고_이전_요약은_stale이다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        answer("진행 중 요약");
        worker.process(goalId);
        jdbc.update("update goal set end_date = '2026-09-30' where goal_id = ?", goalId);
        note(user, id, "800점");
        worker.processDue();
        JsonNode result = summary(user, goalId);
        assertThat(result.get("status").asString()).isEqualTo("INACTIVE");
        assertThat(result.get("stale").asBoolean()).isTrue();
        assertThat(result.get("summary").asString()).isEqualTo("진행 중 요약");
        assertThat(records(user, goalId).at("/content/0/statusNote").asString()).isEqualTo("800점");
        call(post("/api/v1/goals/" + goalId + "/progress-summary/retry"), user, null, 409);
        assertThat(calls()).isEqualTo(1);
    }

    @Test
    void 목표_정보와_전체_투두수가_바뀌면_요약을_갱신한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        answer("처음");
        worker.process(goalId);
        todo(user, goalId);
        assertThat(summary(user, goalId).get("stale").asBoolean()).isTrue();
        TIME.advance(60);
        answer("완료율 변경");
        worker.process(goalId);
        assertThat(json.readTree(AI.requests().getLast().messages().getFirst().content())
                .get("progressRate").decimalValue()).isEqualByComparingTo("33.3");
        call(patch("/api/v1/goals/" + goalId), user, Map.of("title", "새 목표 제목"), 200);
        TIME.advance(60);
        answer("제목 변경");
        worker.process(goalId);
        assertThat(AI.requests().getLast().messages().getFirst().content()).contains("새 목표 제목");
    }

    @Test
    void 메모를_지우면_빈_목록과_EMPTY이며_AI를_추가_호출하지_않는다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        answer("이전 요약");
        worker.process(goalId);
        note(user, id, "");
        worker.process(goalId);
        assertThat(records(user, goalId).get("totalElements").asLong()).isZero();
        assertThat(summary(user, goalId).get("status").asString()).isEqualTo("EMPTY");
        assertThat(summary(user, goalId).get("summary").isNull()).isTrue();
        assertThat(calls()).isEqualTo(1);
    }

    @Test
    void 최근20개만_요약하고_전체_이력과_유니코드_원문은_페이지로_보존한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        for (int i = 0; i < 21; i++) record(user, goalId, "🙂".repeat(350));
        answer("최근 기록 기준");
        worker.process(goalId);
        JsonNode result = summary(user, goalId);
        assertThat(result.get("usedRecordCount").asInt()).isEqualTo(20);
        assertThat(result.get("sourceRecordCount").asLong()).isEqualTo(21);
        JsonNode input = json.readTree(AI.requests().getLast().messages().getFirst().content());
        String clipped = input.at("/records/0/statusNote").asString();
        assertThat(clipped.codePointCount(0, clipped.length())).isEqualTo(300);
        assertThat(records(user, goalId).at("/content/0/statusNote").asString()).isEqualTo("🙂".repeat(350));
        JsonNode second = call(get("/api/v1/goals/" + goalId + "/progress-records").param("page", "1"),
                user, null, 200).get("data");
        assertThat(second.get("content").size()).isEqualTo(1);
        assertThat(second.get("totalElements").asLong()).isEqualTo(21);
    }

    @Test
    void 소유권과_페이징을_검증하고_미완료와_삭제된_기록을_제외한다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long pending = todo(user, goalId);
        note(user, pending, "미완료 메모");
        assertThat(records(user, goalId).get("totalElements").asLong()).isZero();
        call(patch("/api/v1/todos/" + pending + "/complete"), user, null, 200);
        assertThat(records(user, goalId).get("totalElements").asLong()).isEqualTo(1);
        call(get("/api/v1/goals/" + goalId + "/progress-records"), user(), null, 403);
        call(get("/api/v1/goals/" + goalId + "/progress-summary"), null, null, 401);
        call(get("/api/v1/goals/" + goalId + "/progress-records").param("size", "101"), user, null, 400);
        call(delete("/api/v1/todos/" + pending), user, null, 200);
        assertThat(records(user, goalId).get("totalElements").asLong()).isZero();
    }

    @Test
    void 원본_저장이_롤백되면_갱신_대기도_같이_롤백된다() throws Exception {
        User user = user();
        long goalId = goal(user);
        long id = record(user, goalId, "700점");
        long before = jdbc.queryForObject("select revision from goal_progress_summary where goal_id = ?", Long.class,
                goalId);
        transaction.executeWithoutResult(tx -> {
            var request = new UpdateTodoNoteRequest();
            request.setStatusNote("롤백될 글");
            todoService.updateNote(user.id(), id, request);
            tx.setRollbackOnly();
        });
        assertThat(records(user, goalId).at("/content/0/statusNote").asString()).isEqualTo("700점");
        assertThat(jdbc.queryForObject("select revision from goal_progress_summary where goal_id = ?", Long.class,
                goalId)).isEqualTo(before);
    }

    @Test
    void AI_대기_중_탈퇴하면_늦은_결과가_요약을_복원하지_않는다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        AI.onNextAnswer(() -> {
            try {
                call(post("/api/v1/users/me/withdraw"), user, Map.of("password", "password1234!"), 200);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        answer("저장되면 안 되는 결과");
        worker.process(goalId);
        assertThat(jdbc.queryForObject("select count(*) from goal_progress_summary where goal_id = ?", Integer.class,
                goalId)).isZero();
        assertThat(users.existsById(user.id())).isFalse();
    }

    @Test
    void AI_대기_중_목표가_삭제되면_결과를_저장하지_않는다() throws Exception {
        User user = user();
        long goalId = goal(user);
        record(user, goalId, "700점");
        AI.onNextAnswer(() -> {
            jdbc.update("update goal set end_date = '2026-09-30' where goal_id = ?", goalId);
            try {
                call(delete("/api/v1/goals/" + goalId), user, null, 200);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        answer("저장되면 안 되는 결과");
        worker.process(goalId);
        call(get("/api/v1/goals/" + goalId + "/progress-summary"), user, null, 404);
        assertThat(jdbc.queryForObject("select summary from goal_progress_summary where goal_id = ?", String.class,
                goalId)).isNull();
    }
}
