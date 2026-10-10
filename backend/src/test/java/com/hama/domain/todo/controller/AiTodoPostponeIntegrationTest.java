package com.hama.domain.todo.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.goalai.GoalAiTestSupport;
import com.hama.domain.goal.entity.*;
import com.hama.domain.goal.repository.*;
import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.repository.TodoRepository;
import com.hama.domain.todo.service.AiTodoPostponeService;
import com.hama.domain.todo.service.TodoService;
import com.hama.domain.todo.dto.PostponeTodoRequest;
import com.hama.domain.goalai.service.GoalAiReplanService;
import com.hama.domain.goalai.service.BusyTimeReader;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import static org.mockito.Mockito.*;

class AiTodoPostponeIntegrationTest extends GoalAiTestSupport {
    @Autowired GoalRepository goals;
    @Autowired MilestoneRepository milestones;
    @Autowired PeriodGoalRepository periods;
    @Autowired TodoService todoService;
    @Autowired GoalAiReplanService replan;
    @MockitoSpyBean BusyTimeReader busy;
    @Autowired AiTodoPostponeService postpones;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean TodoRepository todos;

    long goal(User u, String end) throws Exception {
        return call(post("/api/v1/goals"), u, Map.of("title", "미루기 검증", "startDate", "2026-09-20", "endDate", end), 201)
                .at("/data/goalId").asLong();
    }
    long todo(User u, long goal, String date, String start, String end) throws Exception {
        var body = new HashMap<String,Object>(Map.of("category", "AI_GOAL_TASK", "content", "할 일", "goalId", goal, "todoDate", date));
        if (start != null) {body.put("startTime", start); body.put("endTime", end);}
        return call(post("/api/v1/todos"), u, body, 201).at("/data/todoId").asLong();
    }
    long task(User u, String date, String start, String end) throws Exception {
        var body = new HashMap<String,Object>(Map.of("category", "TASK", "content", "일반", "todoDate", date));
        if (start != null) {body.put("startTime", start); body.put("endTime", end);}
        return call(post("/api/v1/todos"), u, body, 201).at("/data/todoId").asLong();
    }
    long schedule(User u, String type, String start, String end, boolean allDay, String repeat) throws Exception {
        var body = new HashMap<String,Object>(Map.of("type", type, "title", "보호 일정", "startAt", start, "endAt", end, "allDay", allDay));
        if (repeat != null) body.put("repeatRule", repeat);
        return call(post("/api/v1/calendar/schedules"), u, body, 201).at("/data/scheduleId").asLong();
    }
    JsonNode move(User u, long id, Object body, int status) throws Exception {
        return call(patch("/api/v1/todos/" + id + "/postpone"), u, body, status);
    }
    JsonNode day(User u, String date, Object body) throws Exception {
        return call(post("/api/v1/calendar/days/" + date + "/postpone"), u, body, 200).get("data");
    }
    Map<String,Object> row(long id) {
        return jdbc.queryForMap("select todo_date,start_time,end_time,postponed_count,updated_at,period_goal_id from todo where todo_id=?", id);
    }
    void assertSlot(long id, String date, String start, String end, int count) {
        Todo t = todos.findById(id).orElseThrow();
        assertThat(t.getTodoDate()).isEqualTo(LocalDate.parse(date));
        assertThat(t.getStartTime()).isEqualTo(start == null ? null : LocalTime.parse(start));
        assertThat(t.getEndTime()).isEqualTo(end == null ? null : LocalTime.parse(end));
        assertThat(t.getPostponedCount()).isEqualTo(count);
    }

    @Test void 자동은_다음날_기존길이와_기간목표를_보존하고_캘린더와_ICS에_반영된다() throws Exception {
        User u = user(); long g = goal(u, "2026-10-31");
        long t = todo(u,g,"2026-10-04","19:00","20:15");
        var m = milestones.saveAndFlush(Milestone.create(g,1,"단계",null,LocalDate.parse("2026-09-20"),LocalDate.parse("2026-10-31")));
        long p = periods.saveAndFlush(PeriodGoal.create(m,PeriodType.WEEKLY,1,"주",LocalDate.parse("2026-09-28"),LocalDate.parse("2026-10-04"))).getId();
        jdbc.update("update todo set period_goal_id=? where todo_id=?",p,t);
        var response = move(u,t,null,200).get("data");
        assertThat(response.get("startTime").asString()).isEqualTo("09:00");
        assertSlot(t,"2026-10-05","09:00","10:15",1);
        assertThat(todos.findById(t).orElseThrow().getPeriodGoalId()).isEqualTo(p);
        var items = call(get("/api/v1/calendar").param("from","2026-10-05").param("to","2026-10-05").param("types","AI_GOAL"),u,null,200).at("/data/items");
        assertThat(items.size()).isOne();
        String ics = mvc.perform(get("/api/v1/calendar/export").param("from","2026-10-05").param("to","2026-10-05")
                .header("Authorization","Bearer " + u.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ics).contains("hama-AI_GOAL-"+t+"@hama.app","20261005T090000","20261005T101500");
    }

    @Test void 밀린_무시간_투두는_오늘_현재시각부터_30분_배치한다() throws Exception {
        User u=user(); long g=goal(u,"2026-10-31"),t=todo(u,g,"2026-09-30",null,null);
        move(u,t,Map.of(),200);
        assertSlot(t,"2026-10-01","12:00","12:30",1);
    }

    @Test void 반복_종일_다일_완료투두를_피하며_무시간과_타인일정은_점유하지않는다() throws Exception {
        User u=user(), other=user();long g=goal(u,"2026-10-31");
        long t=todo(u,g,"2026-10-01","18:00","19:00");
        long fixed=schedule(u,"FIXED","2026-10-02T00:00:00","2026-10-03T00:00:00",true,null);
        schedule(u,"PERSONAL","2026-10-02T23:00:00","2026-10-03T10:03:00",false,null);
        schedule(u,"FIXED","2026-10-01T10:00:00","2026-10-01T11:00:00",false,"FREQ=DAILY");
        long completed=task(u,"2026-10-03","11:00","12:00");
        call(patch("/api/v1/todos/"+completed+"/complete"),u,null,200);
        task(u,"2026-10-03",null,null);
        schedule(other,"FIXED","2026-10-03T00:00:00","2026-10-04T00:00:00",true,null);
        var before=jdbc.queryForMap("select * from schedule where schedule_id=?",fixed);
        move(u,t,null,200);
        assertSlot(t,"2026-10-03","12:00","13:00",1);
        assertThat(jdbc.queryForMap("select * from schedule where schedule_id=?",fixed)).isEqualTo(before);
    }

    @Test void 직접지정_생략유지_null삭제_시간쌍_날짜필수_목표기간_충돌을_검증한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31"), t=todo(u,g,"2026-10-01","07:03","08:03");
        for (String body:List.of("{\"startTime\":\"10:00\"}","{\"targetDate\":null}","{\"targetDate\":\"2026-10-02\",\"startTime\":null}",
                "{\"targetDate\":\"2026-10-01\"}","{\"targetDate\":\"2026-11-01\"}","{\"targetDate\":\"2026-02-30\"}")) {
            var before=row(t);move(u,t,body,400);assertThat(row(t)).isEqualTo(before);
        }
        move(u,t,Map.of("targetDate","2026-10-02"),200); // 자동 작업 시간대·10분 제한은 직접 지정에 적용하지 않음
        assertSlot(t,"2026-10-02","07:03","08:03",1);
        long blocked=task(u,"2026-10-03","07:00","09:00");
        var before=row(t);
        assertThat(move(u,t,Map.of("targetDate","2026-10-03"),409).at("/error/code").asString()).isEqualTo("TODO_POSTPONE_TIME_CONFLICT");
        assertThat(row(t)).isEqualTo(before);
        call(delete("/api/v1/todos/"+blocked),u,null,200);
        move(u,t,"{\"targetDate\":\"2026-10-03\",\"startTime\":null,\"endTime\":null}",200);
        assertSlot(t,"2026-10-03",null,null,2);
    }

    @Test void 직접지정은_오늘_이전과_오늘의_지난시간을_거절한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31"),t=todo(u,g,"2026-09-28","09:00","10:00");
        var before=row(t);
        move(u,t,Map.of("targetDate","2026-09-30"),400);
        move(u,t,Map.of("targetDate","2026-10-01"),400);
        assertThat(row(t)).isEqualTo(before);
        move(u,t,Map.of("targetDate","2026-10-01","startTime","12:00","endTime","12:30"),200);
    }

    @Test void 종료일_슬롯부족_완료_비진행_권한실패는_원본을_보존한다() throws Exception {
        User u=user(),other=user();long g=goal(u,"2026-10-02"),t=todo(u,g,"2026-10-02",null,null);
        var before=row(t);
        assertThat(move(u,t,null,409).at("/error/code").asString()).isEqualTo("TODO_POSTPONE_NO_SLOT");
        move(other,t,null,403);move(null,t,null,401);move(u,Long.MAX_VALUE,null,404);
        assertThat(row(t)).isEqualTo(before);
        jdbc.update("update goal set status='PLANNING' where goal_id=?",g);
        assertThat(move(u,t,null,409).at("/error/code").asString()).isEqualTo("TODO_GOAL_NOT_IN_PROGRESS");
        jdbc.update("update goal set status='IN_PROGRESS' where goal_id=?",g);
        call(patch("/api/v1/todos/"+t+"/complete"),u,null,200);
        assertThat(move(u,t,null,409).at("/error/code").asString()).isEqualTo("TODO_ALREADY_COMPLETED");
        call(delete("/api/v1/todos/"+t),u,null,200);move(u,t,null,404);
    }

    @Test void NEXT_DAY는_첫날만_탐색하고_기본전략은_다음빈날을_찾는다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31"),t=todo(u,g,"2026-10-01",null,null);
        schedule(u,"FIXED","2026-10-02T00:00:00","2026-10-03T00:00:00",true,null);
        var before=row(t);
        var failed=day(u,"2026-10-01",Map.of("strategy","NEXT_DAY"));
        assertThat(failed.get("movedCount").asInt()).isZero();
        assertThat(failed.at("/unplaced/0/reason").asString()).isEqualTo("TODO_POSTPONE_NO_SLOT");
        assertThat(row(t)).isEqualTo(before);
        var moved=day(u,"2026-10-01",null);
        assertThat(moved.at("/moved/0/from").isNull()).isTrue();
        assertThat(moved.at("/moved/0/fromDate").asString()).isEqualTo("2026-10-01");
        assertThat(moved.at("/moved/0/to").asString()).isEqualTo("2026-10-03T09:00:00");
        assertSlot(t,"2026-10-03","09:00","09:30",1);
        assertThat(day(u,"2026-10-01",Map.of()).get("movedCount").asInt()).isZero();
    }

    @Test void 일괄은_시간_ID순으로_서로겹치지않게_배치하며_예상실패만_부분성공한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-02"),g2=goal(u,"2026-10-02");
        long untimed=todo(u,g,"2026-10-01",null,null);
        long first=todo(u,g2,"2026-10-01","09:00","10:00"),second=todo(u,g,"2026-10-01","09:00","10:00");
        long planning=todo(u,goal(u,"2026-10-31"),"2026-10-01",null,null);
        jdbc.update("update goal set status='PLANNING' where goal_id=(select goal_id from todo where todo_id=?)",planning);
        long ordinary=task(u,"2026-10-01","14:00","15:00");
        long completed=todo(u,g,"2026-10-01","18:00","19:00");
        call(patch("/api/v1/todos/"+completed+"/complete"),u,null,200);
        schedule(u,"PERSONAL","2026-10-02T11:00:00","2026-10-02T22:00:00",false,null);
        var untouched=List.of(row(untimed),row(planning),row(ordinary),row(completed));
        var result=day(u,"2026-10-01",Map.of());
        assertThat(result.get("movedCount").asInt()).isEqualTo(2);
        assertThat(result.at("/moved/0/todoId").asLong()).isEqualTo(first);
        assertThat(result.at("/moved/1/todoId").asLong()).isEqualTo(second);
        assertThat(result.at("/moved/0/from").asString()).isEqualTo("2026-10-01T09:00:00");
        assertSlot(first,"2026-10-02","09:00","10:00",1);assertSlot(second,"2026-10-02","10:00","11:00",1);
        assertThat(result.get("unplaced").valueStream().map(n->n.get("reason").asString())).containsExactly("TODO_POSTPONE_NO_SLOT","TODO_GOAL_NOT_IN_PROGRESS");
        assertThat(List.of(row(untimed),row(planning),row(ordinary),row(completed))).isEqualTo(untouched);
    }

    @Test void 전략과_날짜형식_인증_OpenAPI계약을_검증한다() throws Exception {
        User u=user();
        for(String strategy:List.of("null","0","true","\"0\"","\"next_day\"","\"BAD\""))
            call(post("/api/v1/calendar/days/2026-10-01/postpone"),u,"{\"strategy\":"+strategy+"}",400);
        for(String date:List.of("2026-02-30","2026-1-01","0999-12-31","10000-01-01"))
            call(post("/api/v1/calendar/days/"+date+"/postpone"),u,null,400);
        call(post("/api/v1/calendar/days/2026-10-01/postpone"),null,null,401);
        assertThat(day(u,"2026-10-01",null).get("unplaced").isEmpty()).isTrue();
        var doc=call(get("/v3/api-docs"),null,null,200);
        assertThat(doc.get("paths").get("/api/v1/calendar/days/{date}/postpone").has("post")).isTrue();
        assertThat(doc.toString()).contains("fromDate","TODO_POSTPONE_NO_SLOT").doesNotContain("TODO_AI_POSTPONE_NOT_SUPPORTED");
    }

    @Test void 탐색은_366번째날까지_허용하고_367번째날은_허용하지않는다() throws Exception {
        User u=user();long g=goal(u,"2028-10-31"),t=todo(u,g,"2026-10-01",null,null);
        schedule(u,"FIXED","2026-10-02T00:00:00","2027-10-02T00:00:00",true,null);
        move(u,t,null,200);assertSlot(t,"2027-10-02","09:00","09:30",1);
        User v=user();long h=goal(v,"2028-10-31"),s=todo(v,h,"2026-10-01",null,null);
        schedule(v,"FIXED","2026-10-02T00:00:00","2027-10-03T00:00:00",true,null);
        var before=row(s);move(v,s,null,409);assertThat(row(s)).isEqualTo(before);
    }

    @Test void 최대날짜와_작업시간보다긴투두는_무한탐색없이_실패한다() throws Exception {
        User u=user();long g=goal(u,"9999-12-31"),t=todo(u,g,"9999-12-30",null,null);
        move(u,t,null,200);assertSlot(t,"9999-12-31","09:00","09:30",1);
        var before=row(t);move(u,t,null,409);assertThat(row(t)).isEqualTo(before);
        long longTask=todo(u,g,"2026-10-01","00:00","23:00");before=row(longTask);move(u,longTask,null,409);assertThat(row(longTask)).isEqualTo(before);
    }

    List<TodoRepository.PostponeCandidate> candidates(long userId) {
        return jdbc.query("""
                select t.todo_id, t.goal_id from todo t join goal g on g.goal_id=t.goal_id
                where t.user_id=? and t.todo_date='2026-10-01' and t.deleted_at is null
                and t.category='AI_GOAL_TASK' and t.status='PENDING' and g.deleted_at is null order by t.todo_id
                """, (rs, n) -> {
            long id=rs.getLong(1), goal=rs.getLong(2);
            return new TodoRepository.PostponeCandidate() {
                public Long getId() {return id;}
                public Long getGoalId() {return goal;}
            };
        }, userId);
    }

    @Test void 중복_일괄요청은_목표잠금후_상태를_재확인하여_한번만_옮긴다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31"),t=todo(u,g,"2026-10-01",null,null);
        CountDownLatch projected=new CountDownLatch(2);
        doAnswer(inv->{Object r=candidates(u.id());projected.countDown();return r;})
                .when(todos).findPostponeCandidates(u.id(),LocalDate.parse("2026-10-01"));
        var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> jobs=new ArrayList<>();
            tx.executeWithoutResult(s->{
                goals.findActiveForUpdate(g).orElseThrow();
                for(int i=0;i<2;i++)jobs.add(pool.submit(()->postpones.postponeDay(u.id(),LocalDate.parse("2026-10-01"),"NEXT_FREE_SLOT").movedCount()));
                try{assertThat(projected.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
            });
            assertThat(List.of(jobs.get(0).get(10,TimeUnit.SECONDS),jobs.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(1,0);
            assertSlot(t,"2026-10-02","09:00","09:30",1);
        } finally {reset(todos);}
    }

    @Test void 잠금대기중_완료_삭제_다른날로_재배치된_후보는_일괄대상에서_제외한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31");
        long completed=todo(u,g,"2026-10-01",null,null),deleted=todo(u,g,"2026-10-01",null,null),replanned=todo(u,g,"2026-10-01",null,null);
        long remaining=todo(u,g,"2026-10-01",null,null);
        CountDownLatch projected=new CountDownLatch(1);
        doAnswer(inv->{Object r=candidates(u.id());projected.countDown();return r;})
                .when(todos).findPostponeCandidates(u.id(),LocalDate.parse("2026-10-01"));
        var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newSingleThreadExecutor()) {
            List<Future<Integer>> jobs=new ArrayList<>();
            tx.executeWithoutResult(s->{
                goals.findActiveForUpdate(g).orElseThrow();
                jobs.add(pool.submit(()->postpones.postponeDay(u.id(),LocalDate.parse("2026-10-01"),"NEXT_FREE_SLOT").movedCount()));
                try{assertThat(projected.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
                todoService.complete(u.id(),completed);todoService.delete(u.id(),deleted);
                todos.findActiveForUpdate(replanned).orElseThrow().reschedule(LocalDate.parse("2026-10-02"),LocalTime.of(9,0),LocalTime.of(9,30));
            });
            assertThat(jobs.getFirst().get(10,TimeUnit.SECONDS)).isOne();
            assertSlot(remaining,"2026-10-02","09:30","10:00",1);
            assertThat(row(replanned).get("postponed_count")).isEqualTo(0);
        } finally {reset(todos);}
    }
    @Test void 서로다른_목표기간에서도_이전예약을_보존하고_새구간을_조회한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-02"),h=goal(u,"2026-10-03");
        long a=todo(u,g,"2026-10-01","09:00","22:00"),b=todo(u,h,"2026-10-01",null,null);
        assertThat(day(u,"2026-10-01",null).get("movedCount").asInt()).isEqualTo(2);
        assertSlot(a,"2026-10-02","09:00","22:00",1);assertSlot(b,"2026-10-03","09:00","09:30",1);
    }

    @Test void 예기치않은_점유조회실패는_앞서_옮긴_투두도_롤백한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-02"),h=goal(u,"2026-10-03");
        long a=todo(u,g,"2026-10-01","09:00","10:00"),b=todo(u,h,"2026-10-01",null,null);
        var before=List.of(row(a),row(b));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test failure"))
                .when(busy).read(eq(u.id()),eq(LocalDate.parse("2026-10-03")),eq(LocalDate.parse("2026-10-03")),anySet());
        try {
            call(post("/api/v1/calendar/days/2026-10-01/postpone"),u,null,500);
            assertThat(List.of(row(a),row(b))).isEqualTo(before);
        } finally {reset(busy);}
    }

    @Test void 같은목표_BE2재배치가_먼저끝나면_개별미루기는_최신날짜부터_진행한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31"),t=todo(u,g,"2026-10-01",null,null);
        CountDownLatch projected=new CountDownLatch(1);
        doAnswer(inv->{
            var result=jdbc.query("select user_id,goal_id from todo where todo_id=?",(rs,n)-> {
                long userId=rs.getLong(1),goalId=rs.getLong(2);
                return new TodoRepository.Link() {
                    public Long getUserId(){return userId;}
                    public Long getGoalId(){return goalId;}
                };
            },t).stream().findFirst();
            projected.countDown();return result;
        }).when(todos).findActiveLink(t);
        var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newSingleThreadExecutor()) {
            List<Future<?>> jobs=new ArrayList<>();
            tx.executeWithoutResult(s->{
                goals.findActiveForUpdate(g).orElseThrow();
                jobs.add(pool.submit(()->todoService.postpone(u.id(),t,new PostponeTodoRequest())));
                try{assertThat(projected.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
                replan.replan(u.id(),g,LocalDate.parse("2026-10-02"));
            });
            jobs.getFirst().get(10,TimeUnit.SECONDS);
            assertSlot(t,"2026-10-03","09:00","09:30",1);
        } finally {reset(todos);}
    }

    @Test void 초단위_반복일정은_자동_일괄_직접미루기에서_겹치지않는다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31");
        long automatic=todo(u,g,"2026-10-01",null,null);
        long manual=todo(u,g,"2026-10-01",null,null);
        long batch=todo(u,g,"2026-10-01",null,null);
        schedule(u,"FIXED","2026-10-01T09:00:00","2026-10-01T10:00:01",false,"FREQ=DAILY");
        move(u,automatic,null,200);
        assertSlot(automatic,"2026-10-02","10:10","10:40",1);
        var before=row(manual);
        assertThat(move(u,manual,Map.of("targetDate","2026-10-02","startTime","10:00","endTime","10:10"),409)
                .at("/error/code").asString()).isEqualTo("TODO_POSTPONE_TIME_CONFLICT");
        assertThat(row(manual)).isEqualTo(before);
        move(u,manual,Map.of("targetDate","2026-10-02","startTime","10:01","endTime","10:10"),200);
        assertThat(day(u,"2026-10-01",Map.of("strategy","NEXT_DAY")).get("movedCount").asInt()).isOne();
        assertSlot(batch,"2026-10-02","10:40","11:10",1);
    }

    @Test void 공용_점유보정은_BE2재배치에도_적용되며_일정원본은_유지한다() throws Exception {
        User u=user();long g=goal(u,"2026-10-31"),t=todo(u,g,"2026-10-02","10:00","10:30");
        long s=schedule(u,"PERSONAL","2026-10-02T09:00:00","2026-10-02T10:00:01",false,null);
        var before=jdbc.queryForMap("select * from schedule where schedule_id=?",s);
        replan.replan(u.id(),g,LocalDate.parse("2026-10-02"));
        assertSlot(t,"2026-10-02","10:10","10:40",0);
        assertThat(jdbc.queryForMap("select * from schedule where schedule_id=?",s)).isEqualTo(before);
    }

}
