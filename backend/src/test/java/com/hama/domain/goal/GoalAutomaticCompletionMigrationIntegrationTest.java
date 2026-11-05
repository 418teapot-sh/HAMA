package com.hama.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GoalAutomaticCompletionMigrationIntegrationTest {
    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void 기존_진행중_목표중_투두가_모두_완료된_경우만_자동완료한다() {
        assertThat(jdbc.queryForObject("select database()", String.class)).isEqualTo("hama_test");
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("30").load();
        previous.clean();
        previous.migrate();
        jdbc.update("""
                insert into goal (goal_id,user_id,title,start_date,end_date,status,created_at,updated_at)
                values (1,1,'all',current_date,current_date + interval 30 day,'IN_PROGRESS',now(),now()),
                       (2,1,'partial',current_date,current_date + interval 30 day,'IN_PROGRESS',now(),now()),
                       (3,1,'empty',current_date,current_date + interval 30 day,'IN_PROGRESS',now(),now()),
                       (4,1,'manual',current_date,current_date + interval 30 day,'COMPLETED',now(),now()),
                       (5,1,'past',current_date - interval 30 day,current_date - interval 1 day,'IN_PROGRESS',now(),now())
                """);
        jdbc.update("""
                insert into todo (user_id,goal_id,category,origin,content,todo_date,status,completed_at,postponed_count,created_at,updated_at,calendar_uid)
                values (1,1,'AI_GOAL_TASK','MANUAL','done',current_date,'COMPLETED',now(),0,now(),now(),'test-1'),
                       (1,2,'AI_GOAL_TASK','MANUAL','pending',current_date,'PENDING',null,0,now(),now(),'test-2'),
                       (1,5,'AI_GOAL_TASK','MANUAL','past done',current_date,'COMPLETED',now(),0,now(),now(),'test-3')
                """);
        flyway.migrate();
        assertThat(jdbc.queryForList("select status from goal order by goal_id", String.class))
                .containsExactly("COMPLETED", "IN_PROGRESS", "IN_PROGRESS", "COMPLETED", "IN_PROGRESS");
        assertThat(jdbc.queryForList("select auto_completed from goal order by goal_id", Boolean.class))
                .containsExactly(true, false, false, false, false);
    }
}
