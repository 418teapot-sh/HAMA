package com.hama.domain.goal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.goal.entity.Milestone;
import com.hama.domain.goal.entity.PeriodGoal;
import com.hama.domain.goal.entity.PeriodType;
import com.hama.domain.goal.repository.MilestoneRepository;
import com.hama.domain.goal.repository.PeriodGoalRepository;
import com.hama.domain.goal.service.GoalService;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.domain.user.repository.UserRepository;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalApiIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @TestBean(name = "be3Clock", methodName = "fixedClock")
    private Clock clock;

    static Clock fixedClock() {
        // UTC 10월 3일 / KST 10월 4일로 서버 기본 시간대 의존을 검출합니다.
        return Clock.fixed(Instant.parse("2026-10-03T15:05:06Z"), ZoneId.of("Asia/Seoul"));
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MilestoneRepository milestones;
    @Autowired
    private PeriodGoalRepository periodGoals;
    @Autowired
    private TodoRepository todos;
    @Autowired
    private GoalService goalService;
    @Autowired
    private TransactionTemplate transaction;

    private record Session(Long userId, String token) {
    }

    private Session signup() throws Exception {
        String email = "goal-" + UUID.randomUUID() + "@hama.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234!","name":"목표","termsAgreed":true,"privacyAgreed":true,"ageConfirmed":true}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn();
        return new Session(users.findByEmail(email).orElseThrow().getId(),
                body(result).at("/data/accessToken").asString());
    }

    private long createGoal(Session user, String title, LocalDate start, LocalDate end) throws Exception {
        MvcResult result = mvc.perform(auth(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"체력 기르기","metricName":"체중","unit":"kg",
                                 "startValue":80.0,"targetValue":74.0,"weeklyAvailableHours":5.0,
                                 "startDate":"%s","endDate":"%s"}
                                """.formatted(title, start, end)), user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn();
        return body(result).at("/data/goalId").asLong();
    }

    private long createGoal(Session user) throws Exception {
        return createGoal(user, "매일 러닝 30분", TODAY, TODAY.plusDays(60));
    }

    /** API 로는 과거 종료일을 넣을 수 없으므로 DB 를 직접 바꿔 지난 목표를 만듭니다. */
    private void makePast(long goalId) {
        jdbc.update("UPDATE goal SET start_date = ?, end_date = ? WHERE goal_id = ?",
                Date.valueOf(TODAY.minusDays(30)), Date.valueOf(TODAY.minusDays(1)), goalId);
    }

    /** PLANNING 목표는 AI 흐름에서만 만들어지므로 DB 를 직접 바꿉니다. */
    private void makePlanning(long goalId) {
        jdbc.update("UPDATE goal SET status = 'PLANNING' WHERE goal_id = ?", goalId);
    }

    /** 투두 API 를 거치면 목표 상태·기간 검사까지 맞춰야 해서, AI_GOAL_TASK 는 SQL 로 바로 넣습니다. */
    private long goalTask(Session user, long goalId, Long periodGoalId, boolean completed) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO todo (user_id, goal_id, period_goal_id, category, content, todo_date,
                                      status, postponed_count, completed_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'AI_GOAL_TASK', '단어 50개', ?, ?, 0, ?, NOW(6), NOW(6))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, user.userId());
            ps.setLong(2, goalId);
            ps.setObject(3, periodGoalId);
            ps.setDate(4, Date.valueOf(TODAY));
            ps.setString(5, completed ? "COMPLETED" : "PENDING");
            ps.setTimestamp(6, completed ? Timestamp.valueOf(TODAY.atTime(9, 0)) : null);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Session user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
    }

    private JsonNode body(MvcResult result) {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    // ---- 생성 · 상세 · 수정 ----

    @Test
    void 로그인하지_않으면_모든_목표_API는_401() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/goals"), post("/api/v1/goals").content("{}"),
                get("/api/v1/goals/1"), get("/api/v1/goals/1/tree"),
                patch("/api/v1/goals/1").content("{}"), delete("/api/v1/goals/1"))) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void 직접_생성한_목표를_상세_조회한다() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);

        mvc.perform(auth(get("/api/v1/goals/" + goalId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.title").value("매일 러닝 30분"))
                .andExpect(jsonPath("$.data.metricName").value("체중"))
                .andExpect(jsonPath("$.data.unit").value("kg"))
                .andExpect(jsonPath("$.data.startValue").value(80.0))
                .andExpect(jsonPath("$.data.weeklyAvailableHours").value(5.0))
                .andExpect(jsonPath("$.data.startDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.progressRate").value(0.0))
                .andExpect(jsonPath("$.data.realityVerdict").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void 생성_필수값과_기간을_검증한다() throws Exception {
        Session owner = signup();
        mvc.perform(auth(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("{}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.title").exists())
                .andExpect(jsonPath("$.error.fields.startDate").exists())
                .andExpect(jsonPath("$.error.fields.endDate").exists());

        for (String period : List.of(
                "\"startDate\":\"2026-10-10\",\"endDate\":\"2026-10-05\"",
                "\"startDate\":\"2026-09-01\",\"endDate\":\"2026-10-03\"")) {
            mvc.perform(auth(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"목표\",%s}".formatted(period)), owner))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("GOAL_INVALID_PERIOD"));
        }

        mvc.perform(auth(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"목표","startDate":"2026-10-04","endDate":"2026-10-30","weeklyAvailableHours":200}
                        """), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.weeklyAvailableHours").exists());

        // 종료일이 오늘이면 아직 진행 중이므로 만들 수 있습니다.
        createGoal(owner, "오늘까지", TODAY, TODAY);

        mvc.perform(auth(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"목표","startDate":"2026-10-04","endDate":"9999-12-31"}
                        """), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_PERIOD_TOO_LONG"));
        createGoal(owner, "365일", TODAY, TODAY.plusDays(364));
    }

    @Test
    void DB_에_담을_수_없는_설명과_날짜는_500_대신_400() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);
        String longDescription = "a".repeat(70 * 1024);

        mvc.perform(auth(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"목표","description":"%s","startDate":"2026-10-04","endDate":"2026-10-30"}
                        """.formatted(longDescription)), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_DESCRIPTION_TOO_LONG"));
        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"%s\"}".formatted(longDescription)), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_DESCRIPTION_TOO_LONG"));

        mvc.perform(auth(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"목표","startDate":"2026-10-04","endDate":"+10000-01-01"}
                        """), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_INVALID_PERIOD"));
        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"+10000-01-01\"}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_INVALID_PERIOD"));
    }

    @Test
    void 남의_목표는_403_없는_목표는_404() throws Exception {
        Session owner = signup();
        Session other = signup();
        long goalId = createGoal(owner);

        mvc.perform(auth(get("/api/v1/goals/" + goalId), other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"탈취\"}"), other))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/goals/999999999"), owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GOAL_NOT_FOUND"));
    }

    @Test
    void 수정은_보낸_필드만_바꾸고_상세를_돌려준다() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);

        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"3개월 안에 토익 850점","endDate":"2026-12-31","targetValue":850,"weeklyAvailableHours":12.0}
                        """), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("3개월 안에 토익 850점"))
                .andExpect(jsonPath("$.data.endDate").value("2026-12-31"))
                .andExpect(jsonPath("$.data.targetValue").value(850))
                .andExpect(jsonPath("$.data.weeklyAvailableHours").value(12.0))
                .andExpect(jsonPath("$.data.metricName").value("체중"))
                .andExpect(jsonPath("$.data.startDate").value(TODAY.toString()));

        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.title").exists());
        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-10-01\"}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_INVALID_PERIOD"));
    }

    @Test
    void 수정에서_null_은_값을_지우고_생략한_필드와_title_은_유지한다() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);

        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":null,"metricName":null,"unit":null,"startValue":null}
                        """), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("매일 러닝 30분"))
                .andExpect(jsonPath("$.data.metricName").isEmpty())
                .andExpect(jsonPath("$.data.unit").isEmpty())
                .andExpect(jsonPath("$.data.startValue").isEmpty())
                .andExpect(jsonPath("$.data.targetValue").value(74.0))
                .andExpect(jsonPath("$.data.description").value("체력 기르기"));

        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":null}"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_INVALID_INPUT"));
    }

    @Test
    void 지난_목표는_수정하면_409() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);
        makePast(goalId);

        mvc.perform(auth(get("/api/v1/goals/" + goalId), owner))
                .andExpect(jsonPath("$.data.status").value("PAST"));
        mvc.perform(auth(patch("/api/v1/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-12-31\"}"), owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOAL_NOT_EDITABLE"));
    }

    @Test
    void 진행률은_삭제되지_않은_AI_GOAL_TASK_만_센다() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);
        goalTask(owner, goalId, null, true);
        goalTask(owner, goalId, null, false);
        goalTask(owner, goalId, null, false);
        long removed = goalTask(owner, goalId, null, true);
        jdbc.update("UPDATE todo SET deleted_at = NOW(6) WHERE todo_id = ?", removed);

        mvc.perform(auth(get("/api/v1/goals/" + goalId), owner))
                .andExpect(jsonPath("$.data.progressRate").value(33.3));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todo WHERE goal_id = ?", Long.class, goalId))
                .isEqualTo(4L);
    }

    // ---- 리스트 · 삭제 ----

    @Test
    void 리스트는_status_로_진행_중과_지난_목표를_나누고_본인_목표만_보여준다() throws Exception {
        Session owner = signup();
        long later = createGoal(owner, "나중 마감", TODAY, TODAY.plusDays(90));
        long sooner = createGoal(owner, "곧 마감", TODAY, TODAY.plusDays(10));
        long past = createGoal(owner, "지난 목표", TODAY, TODAY.plusDays(5));
        makePast(past);
        createGoal(signup());
        goalTask(owner, sooner, null, true);
        goalTask(owner, sooner, null, false);

        mvc.perform(auth(get("/api/v1/goals").param("status", "IN_PROGRESS"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[0].goalId").value(sooner))
                .andExpect(jsonPath("$.data.content[0].progressRate").value(50.0))
                .andExpect(jsonPath("$.data.content[1].goalId").value(later))
                .andExpect(jsonPath("$.data.content[1].progressRate").value(0.0));

        mvc.perform(auth(get("/api/v1/goals").param("status", "PAST"), owner))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].goalId").value(past))
                .andExpect(jsonPath("$.data.content[0].status").value("PAST"));

        mvc.perform(auth(get("/api/v1/goals"), owner))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void 리스트는_페이지를_나누고_잘못된_파라미터는_400() throws Exception {
        Session owner = signup();
        for (int i = 0; i < 3; i++) {
            createGoal(owner);
        }

        mvc.perform(auth(get("/api/v1/goals").param("page", "1").param("size", "2"), owner))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mvc.perform(auth(get("/api/v1/goals").param("status", "PLANNING"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BINDING_ERROR"));
        for (String[] params : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                new String[]{"size", "101"}, new String[]{"page", String.valueOf(Integer.MAX_VALUE)})) {
            mvc.perform(auth(get("/api/v1/goals").param(params[0], params[1]), owner))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void 진행_중인_목표는_삭제하면_409() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);

        mvc.perform(auth(delete("/api/v1/goals/" + goalId), owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOAL_NOT_DELETABLE"));
    }

    @Test
    void PLANNING_목표는_전체_리스트에만_나오고_삭제할_수_있다() throws Exception {
        Session owner = signup();
        long inProgress = createGoal(owner);
        long planning = createGoal(owner, "플랜 고르는 중", TODAY, TODAY.plusDays(30));
        makePlanning(planning);
        long task = goalTask(owner, planning, null, false);

        mvc.perform(auth(get("/api/v1/goals"), owner))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].goalId").value(planning))
                .andExpect(jsonPath("$.data.content[0].status").value("PLANNING"));
        mvc.perform(auth(get("/api/v1/goals").param("status", "IN_PROGRESS"), owner))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].goalId").value(inProgress));

        mvc.perform(auth(delete("/api/v1/goals/" + planning), owner))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/goals/" + planning), owner))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM todo WHERE todo_id = ?",
                Boolean.class, task)).isTrue();
        mvc.perform(auth(get("/api/v1/goals"), owner))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 같은_트랜잭션에서_불러온_투두가_있어도_목표_삭제가_투두_삭제를_되돌리지_않는다() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);
        long task = goalTask(owner, goalId, null, false);
        makePast(goalId);

        transaction.executeWithoutResult(status -> {
            Todo loaded = todos.findActiveForUpdate(task).orElseThrow();
            loaded.changeStatusNote("메모");
            goalService.delete(owner.userId(), goalId);
        });

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM todo WHERE todo_id = ?",
                Boolean.class, task)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status_note FROM todo WHERE todo_id = ?",
                String.class, task)).isEqualTo("메모");
    }

    @Test
    void 지난_목표를_삭제하면_연결된_투두도_사라지고_다시_조회하면_404() throws Exception {
        Session owner = signup();
        Session other = signup();
        long goalId = createGoal(owner);
        long task = goalTask(owner, goalId, null, false);
        makePast(goalId);

        mvc.perform(auth(delete("/api/v1/goals/" + goalId), other))
                .andExpect(status().isForbidden());

        mvc.perform(auth(delete("/api/v1/goals/" + goalId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(auth(get("/api/v1/goals/" + goalId), owner))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/goals/" + goalId), owner))
                .andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/goals").param("status", "PAST"), owner))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM todo WHERE todo_id = ?",
                Boolean.class, task)).isTrue();
        mvc.perform(auth(get("/api/v1/todos").param("date", TODAY.toString()), owner))
                .andExpect(jsonPath("$.data.pending.length()").value(0));
    }

    // ---- 계층 트리 ----

    @Test
    void 플랜을_적용하지_않은_목표의_트리는_빈_배열() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);

        mvc.perform(auth(get("/api/v1/goals/" + goalId + "/tree"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.milestones.length()").value(0));
    }

    @Test
    void 트리는_순서대로_마일스톤과_기간_목표를_담고_투두를_집계한다() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);
        Milestone second = milestones.save(Milestone.create(goalId, 2, "실전", null,
                TODAY.plusDays(31), TODAY.plusDays(60)));
        Milestone first = milestones.save(Milestone.create(goalId, 1, "기초 다지기", null,
                TODAY, TODAY.plusDays(30)));
        PeriodGoal week2 = periodGoals.save(PeriodGoal.create(first, PeriodType.WEEKLY, 2, "2주차",
                TODAY.plusDays(7), TODAY.plusDays(13)));
        PeriodGoal week1 = periodGoals.save(PeriodGoal.create(first, PeriodType.WEEKLY, 1, "1주차",
                TODAY, TODAY.plusDays(6)));
        goalTask(owner, goalId, week1.getId(), true);
        goalTask(owner, goalId, week1.getId(), true);
        goalTask(owner, goalId, week1.getId(), false);
        goalTask(owner, goalId, week2.getId(), false);

        mvc.perform(auth(get("/api/v1/goals/" + goalId + "/tree"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.milestones.length()").value(2))
                .andExpect(jsonPath("$.data.milestones[0].milestoneId").value(first.getId()))
                .andExpect(jsonPath("$.data.milestones[0].seq").value(1))
                .andExpect(jsonPath("$.data.milestones[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.milestones[0].progressRate").value(50.0))
                .andExpect(jsonPath("$.data.milestones[0].periodGoals[0].periodGoalId").value(week1.getId()))
                .andExpect(jsonPath("$.data.milestones[0].periodGoals[0].periodType").value("WEEKLY"))
                .andExpect(jsonPath("$.data.milestones[0].periodGoals[0].todoTotal").value(3))
                .andExpect(jsonPath("$.data.milestones[0].periodGoals[0].todoCompleted").value(2))
                .andExpect(jsonPath("$.data.milestones[0].periodGoals[1].periodGoalId").value(week2.getId()))
                .andExpect(jsonPath("$.data.milestones[1].milestoneId").value(second.getId()))
                .andExpect(jsonPath("$.data.milestones[1].periodGoals.length()").value(0));
    }

    @Test
    void 남의_목표_트리는_403_삭제된_목표는_404() throws Exception {
        Session owner = signup();
        long goalId = createGoal(owner);

        mvc.perform(auth(get("/api/v1/goals/" + goalId + "/tree"), signup()))
                .andExpect(status().isForbidden());

        makePast(goalId);
        mvc.perform(auth(delete("/api/v1/goals/" + goalId), owner)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/goals/" + goalId + "/tree"), owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GOAL_NOT_FOUND"));
    }
}
