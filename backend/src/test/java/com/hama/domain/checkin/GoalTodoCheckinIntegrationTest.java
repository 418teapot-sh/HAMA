package com.hama.domain.checkin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hama.domain.goal.entity.*;
import com.hama.domain.goal.repository.*;
import com.hama.domain.goal.service.GoalService;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.domain.todo.service.TodoService;
import com.hama.domain.todo.dto.CreateTodoRequest;
import com.hama.global.exception.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalTodoCheckinIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoalRepository goals;
    @Autowired MilestoneRepository milestones;
    @Autowired PeriodGoalRepository periods;
    @Autowired TodoService todos;
    @Autowired GoalService goalService;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean TodoRepository todoRepository;
    @TestBean(name = "be3Clock", methodName = "clock") Clock be3;

    static Clock clock() { return Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneId.of("Asia/Seoul")); }
    record User(long id, String token) {}

    User user() throws Exception {
        String email = UUID.randomUUID() + "@checkin.test";
        JsonNode r = call(post("/api/auth/signup"), null, Map.of("email", email, "password", "testPassword123", "name", "검증"), 200);
        return new User(jdbc.queryForObject("select user_id from users where email = ?", Long.class, email), r.at("/data/accessToken").asString());
    }
    JsonNode call(MockHttpServletRequestBuilder req, User user, Object body, int status) throws Exception {
        if (user != null) req.header("Authorization", "Bearer " + user.token());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        var response = mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }
    long goal(User u) throws Exception {
        return call(post("/api/v1/goals"), u, Map.of("title", "운동", "unit", "kg", "startDate", "2026-10-01", "endDate", "2026-10-31"), 201).at("/data/goalId").asLong();
    }
    Map<String,Object> input(long goal, String date) {
        return new HashMap<>(Map.of("category", "AI_GOAL_TASK", "content", "목표 할 일", "todoDate", date, "goalId", goal));
    }
    long todo(User u, long goal) throws Exception { return call(post("/api/v1/todos"), u, input(goal, "2026-10-08"), 201).at("/data/todoId").asLong(); }
    long period(long goal) {
        var m = milestones.saveAndFlush(Milestone.create(goal, 1, "단계", null, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")));
        return periods.saveAndFlush(PeriodGoal.create(m, PeriodType.WEEKLY, 1, "주간", LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11"))).getId();
    }
    JsonNode checkin(User u, long g, String type, int status) throws Exception {
        return call(post("/api/v1/todos/state/checkins"), u, Map.of("goalId",g,"type",type), status);
    }
    JsonNode history(User u, long g) throws Exception { return call(get("/api/v1/todos/state/checkins").param("goalId", ""+g), u, null, 200).get("data"); }

    @Test void 목표_연결_완료집계_소프트삭제_캘린더_파일이_이어진다() throws Exception {
        User u=user(); long g=goal(u);
        long a=todo(u,g), b=todo(u,g), c=todo(u,g);
        JsonNode daily=call(get("/api/v1/todos").param("date","2026-10-08"),u,null,200);
        assertThat(daily.at("/data/pending/0/goalId").asLong()).isEqualTo(g);
        assertThat(daily.at("/data/pending/0/goalTitle").asString()).isEqualTo("운동");
        assertThat(call(patch("/api/v1/todos/"+a+"/complete"),u,null,200).at("/data/goalProgressRate").asDouble()).isEqualTo(33.3);
        call(delete("/api/v1/todos/"+c),u,null,200);
        assertThat(call(get("/api/v1/goals/"+g),u,null,200).at("/data/progressRate").asDouble()).isEqualTo(50.0);
        assertThat(call(patch("/api/v1/todos/"+b+"/complete"),u,null,200).at("/data/goalProgressRate").asDouble()).isEqualTo(100.0);
        JsonNode items=call(get("/api/v1/calendar").param("from","2026-10-08").param("to","2026-10-08").param("types","AI_GOAL"),u,null,200).at("/data/items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).get("goalId").asLong()).isEqualTo(g);
        assertThat(items.get(0).get("allDay").asBoolean()).isTrue();
        assertThat(items.get(0).get("status").asString()).isEqualTo("COMPLETED");
        String file=mvc.perform(get("/api/v1/calendar/export").param("from","2026-10-08").param("to","2026-10-08").param("types","AI_GOAL").header("Authorization","Bearer "+u.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(file).contains("UID:hama-AI_GOAL-"+a+"@hama.app", "DTSTART;VALUE=DATE:20261008").doesNotContain("hama-AI_GOAL-"+c+"@");
    }

    @Test void 생성은_본인_진행중_목표와_선택한_기간_목표_범위를_검증한다() throws Exception {
        User u=user(), other=user(); long g=goal(u), otherGoal=goal(other), p=period(g);
        var body=input(g,"2026-10-05");body.put("periodGoalId",p);call(post("/api/v1/todos"),u,body,201);
        body.put("todoDate","2026-10-11");call(post("/api/v1/todos"),u,body,201);
        body.put("todoDate","2026-10-12");call(post("/api/v1/todos"),u,body,400);
        body.put("todoDate","2026-10-08");body.put("periodGoalId",period(otherGoal));call(post("/api/v1/todos"),u,body,403);
        body.put("periodGoalId",period(goal(u)));call(post("/api/v1/todos"),u,body,400);
        body.put("periodGoalId",Long.MAX_VALUE);call(post("/api/v1/todos"),u,body,404);
        call(post("/api/v1/todos"),u,input(otherGoal,"2026-10-08"),403);
        call(post("/api/v1/todos"),u,input(Long.MAX_VALUE,"2026-10-08"),404);
        for(String date:List.of("2026-09-30","2026-11-01"))call(post("/api/v1/todos"),u,input(g,date),400);
        for(String date:List.of("2026-10-01","2026-10-31"))call(post("/api/v1/todos"),u,input(g,date),201);
        jdbc.update("update goal set status='PLANNING' where goal_id=?",g);call(post("/api/v1/todos"),u,input(g,"2026-10-08"),409);
        jdbc.update("update goal set status='IN_PROGRESS', end_date='2026-10-07' where goal_id=?",g);call(post("/api/v1/todos"),u,input(g,"2026-10-07"),409);
    }

    @Test void 종료후_기존투두_완료_메모_삭제는_허용하고_AI미루기는_명시적으로_거절한다() throws Exception {
        User u=user();long g=goal(u),t=todo(u,g);
        assertThat(call(patch("/api/v1/todos/"+t+"/postpone"),u,null,409).at("/error/code").asString()).isEqualTo("TODO_AI_POSTPONE_NOT_SUPPORTED");
        call(patch("/api/v1/todos/"+t),u,Map.of("content","수정","startTime","09:00","endTime","10:00"),200);
        jdbc.update("update goal set end_date='2026-10-07' where goal_id=?",g);
        call(patch("/api/v1/todos/"+t+"/complete"),u,null,200);
        call(patch("/api/v1/todos/"+t+"/complete"),u,null,409);
        call(patch("/api/v1/todos/"+t),u,Map.of("content","불가"),409);
        call(patch("/api/v1/todos/state/"+t),u,Map.of("statusNote","끝"),200);
        call(delete("/api/v1/todos/"+t),u,null,200);
    }

    @Test void 체크인은_자유순서_기본KST_단위_달성여부를_보존하고_목표는_변경하지않는다() throws Exception {
        User u=user();long g=goal(u);
        call(post("/api/v1/todos/state/checkins"),u,Map.of("goalId",g,"type","END","achieved",true,"value",-12.34),201);
        checkin(u,g,"START",201);checkin(u,g,"MID",201);checkin(u,g,"MID",201);
        checkin(u,g,"START",409);checkin(u,g,"END",409);
        JsonNode h=history(u,g);assertThat(h.get("unit").asString()).isEqualTo("kg");
        assertThat(h.at("/checkins/totalElements").asInt()).isEqualTo(4);
        assertThat(h.at("/checkins/content/0/checkedAt").asString()).isEqualTo("2026-10-08T12:00:00");
        assertThat(h.at("/checkins/content/3/achieved").asBoolean()).isTrue();
        assertThat(h.at("/checkins/content/3/value").asDouble()).isEqualTo(-12.34);
        assertThat(jdbc.queryForObject("select status from goal where goal_id=?",String.class,g)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select result_status from goal where goal_id=?",String.class,g)).isNull();
    }

    @Test void 체크인의_미래_타입_달성여부_숫자_메모_범위를_검증한다() throws Exception {
        User u=user();long g=goal(u);
        for (String patch : List.of("\"checkedAt\":\"2026-10-08T12:00:01\"", "\"checkedAt\":\"2026-02-30T00:00:00\"", "\"checkedAt\":\"0999-01-01T00:00:00\"", "\"achieved\":true", "\"achieved\":false", "\"value\":100000000", "\"value\":0.001")) {
            call(post("/api/v1/todos/state/checkins"),u,"{\"goalId\":"+g+",\"type\":\"MID\","+patch+"}",400);
        }
        for (String invalid : List.of("0", "2", "\"0\"", "\"mid\"", "\"INVALID\"")) {
            call(post("/api/v1/todos/state/checkins"),u,"{\"goalId\":"+g+",\"type\":"+invalid+"}",400);
        }
        call(post("/api/v1/todos/state/checkins"),u,Map.of("goalId",g,"type","MID","note","가".repeat(21845),"value",99999999.99,"checkedAt","1000-01-01T00:00:00"),201);
        call(post("/api/v1/todos/state/checkins"),u,Map.of("goalId",g,"type","MID","note","가".repeat(21846)),400);
        call(post("/api/v1/todos/state/checkins"),u,Map.of("goalId",g,"type","MID","checkedAt","1582-10-10T12:34:56"),201);
        assertThat(history(u,g).at("/checkins/content/0/checkedAt").asString()).isEqualTo("1582-10-10T12:34:56");
    }

    @Test void 체크인_페이징은_최신시각_ID순이며_빈목록과_범위를_검증한다() throws Exception {
        User u=user();long g=goal(u);
        assertThat(history(u,g).at("/checkins/content").isEmpty()).isTrue();
        for(String time:List.of("2026-09-01T00:00:00","2026-10-01T00:00:00","2026-10-01T00:00:00"))
            call(post("/api/v1/todos/state/checkins"),u,Map.of("goalId",g,"type","MID","checkedAt",time),201);
        var all=history(u,g).at("/checkins/content");
        assertThat(all.get(0).get("checkinId").asLong()).isGreaterThan(all.get(1).get("checkinId").asLong());
        var page=call(get("/api/v1/todos/state/checkins").param("goalId",""+g).param("page","1").param("size","2"),u,null,200).at("/data/checkins");
        assertThat(page.get("content").size()).isOne();assertThat(page.get("totalElements").asInt()).isEqualTo(3);
        for(String[] pair:List.of(new String[]{"-1","20"},new String[]{"0","0"},new String[]{"0","101"}))
            call(get("/api/v1/todos/state/checkins").param("goalId",""+g).param("page",pair[0]).param("size",pair[1]),u,null,400);
    }

    @Test void 목표삭제시_투두와_체크인접근은_막고_체크인원본은_남긴다() throws Exception {
        User u=user(),other=user();long g=goal(u),t=todo(u,g);
        checkin(other,g,"MID",403);
        call(get("/api/v1/todos/state/checkins").param("goalId",""+g),other,null,403);
        checkin(u,Long.MAX_VALUE,"MID",404);
        checkin(u,g,"MID",201);
        jdbc.update("update goal set status='PLANNING' where goal_id=?",g);checkin(u,g,"MID",409);
        jdbc.update("update goal set status='IN_PROGRESS',end_date='2026-10-07' where goal_id=?",g);
        checkin(u,g,"END",201);
        call(delete("/api/v1/goals/"+g),u,null,200);
        checkin(u,g,"MID",404);
        call(get("/api/v1/todos/state/checkins").param("goalId",""+g),u,null,404);
        call(patch("/api/v1/todos/"+t+"/complete"),u,null,404);
        assertThat(jdbc.queryForObject("select count(*) from goal_checkin where goal_id=?",Integer.class,g)).isEqualTo(2);
        assertThat(call(get("/api/v1/calendar").param("from","2026-10-08").param("to","2026-10-08"),u,null,200).at("/data/items").isEmpty()).isTrue();
    }

    // 동일 트랜잭션의 실제 non-locking SQL 뒤에 latch를 두어 잠금 대기 전 read-view를 확정합니다.
    Optional<TodoRepository.Link> readLink(Long id) {
        return jdbc.<TodoRepository.Link>query("select user_id, goal_id from todo where todo_id=? and deleted_at is null", (rs,row)-> {
            Long owner=rs.getLong("user_id"), goal=rs.getObject("goal_id",Long.class);
            return new TodoRepository.Link() {
                public Long getUserId() {return owner;}
                public Long getGoalId() {return goal;}
            };
        },id).stream().findFirst();
    }

    @Test void 동시완료는_잠금대기후_최신진행률을_반환한다() throws Exception {
        User u=user();long g=goal(u),a=todo(u,g),b=todo(u,g);
        CountDownLatch projected=new CountDownLatch(1);
        doAnswer(inv -> {Object value=readLink(inv.getArgument(0)); if (Objects.equals(inv.getArgument(0),b)) projected.countDown(); return value;}).when(todoRepository).findActiveLink(anyLong());
        var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try(var pool=Executors.newSingleThreadExecutor()) {
            final Future<?>[] pending=new Future<?>[1];
            tx.executeWithoutResult(s->{
                goals.findActiveForUpdate(g).orElseThrow();
                pending[0]=pool.submit(()->todos.complete(u.id(),b));
                try {assertThat(projected.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
                assertThat(todos.complete(u.id(),a).goalProgressRate()).isEqualByComparingTo("50.0");
            });
            var result=(com.hama.domain.todo.dto.TodoResponses.Completion)pending[0].get(5,TimeUnit.SECONDS);
            assertThat(result.goalProgressRate()).isEqualByComparingTo("100.0");
        } finally {reset(todoRepository);}
    }

    @Test void 동시_START는_하나만_성공하고_DB도_중복을_막는다() throws Exception {
        User u=user();long g=goal(u);CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> jobs=new ArrayList<>();
            for(int i=0;i<2;i++)jobs.add(pool.submit(()->{start.await();return mvc.perform(post("/api/v1/todos/state/checkins").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":"+g+",\"type\":\"START\"}")).andReturn().getResponse().getStatus();}));
            start.countDown();assertThat(List.of(jobs.get(0).get(5,TimeUnit.SECONDS),jobs.get(1).get(5,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThatThrownBy(()->jdbc.update("insert into goal_checkin(goal_id,type,checked_at,created_at,updated_at) values (?,'START',now(),now(),now())",g))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void 목표삭제와_경합하는_투두완료는_삭제후_거절한다() throws Exception {
        User u=user();long g=goal(u),t=todo(u,g);jdbc.update("update goal set end_date='2026-10-07' where goal_id=?",g);
        CountDownLatch projected=new CountDownLatch(1);
        doAnswer(inv->{Object v=readLink(inv.getArgument(0));projected.countDown();return v;}).when(todoRepository).findActiveLink(t);
        var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newSingleThreadExecutor()) {
            final Future<?>[] f=new Future<?>[1];
            tx.executeWithoutResult(s->{goals.findActiveForUpdate(g).orElseThrow();f[0]=pool.submit(()->todos.complete(u.id(),t));
                try{assertThat(projected.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
                goalService.delete(u.id(),g);
            });
            assertThatThrownBy(()->f[0].get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
            assertThat(jdbc.queryForObject("select status from todo where todo_id=?",String.class,t)).isEqualTo("PENDING");
        }finally{reset(todoRepository);}
    }

    @Test void 목표투두도_일반투두와_같은_캘린더_예산을_공유한다() throws Exception {
        User u=user(); long g=goal(u);
        for(int i=0;i<100;i++) todos.create(u.id(), new CreateTodoRequest("AI_GOAL_TASK", "할 일", LocalDate.parse("2026-10-08"), null, null, g, null));
        var range=get("/api/v1/calendar").param("from","2026-10-08").param("to","2026-10-08").param("types","AI_GOAL");
        assertThat(call(range,u,null,200).at("/data/items").size()).isEqualTo(100);
        call(post("/api/v1/todos"),u,Map.of("category","TASK","content","일반","todoDate","2026-10-08"),201);
        for(String path:List.of("/api/v1/calendar","/api/v1/calendar/export")) {
            assertThat(call(get(path).param("from","2026-10-08").param("to","2026-10-08"),u,null,400)
                    .at("/error/code").asString()).isEqualTo("CALENDAR_RESULT_LIMIT_EXCEEDED");
        }
    }

    @Test void 인증_및_OpenAPI_체크인생성_페이징계약을_검증한다() throws Exception {
        call(get("/api/v1/todos/state/checkins").param("goalId","1"),null,null,401);
        call(post("/api/v1/todos/state/checkins"),null,Map.of("goalId",1,"type","MID"),401);
        var doc=call(get("/v3/api-docs"),null,null,200);
        var path=doc.get("paths").get("/api/v1/todos/state/checkins");
        assertThat(path.get("post").get("responses").has("201")).isTrue();
        assertThat(path.get("get").get("parameters").valueStream().map(p->p.get("name").asString())).contains("goalId","page","size");
    }
}
