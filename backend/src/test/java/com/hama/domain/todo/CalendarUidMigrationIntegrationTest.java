package com.hama.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
class CalendarUidMigrationIntegrationTest {
    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void V28_기존_투두와_일정의_현재_UID를_보존한다() {
        assertThat(jdbc.queryForObject("select database()", String.class)).isEqualTo("hama_test");
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("28").load();
        previous.clean();
        previous.migrate();
        jdbc.update("""
                insert into todo (user_id,category,origin,content,todo_date,status,postponed_count,created_at,updated_at)
                values (1,'TASK','MANUAL','일반','2026-10-01','PENDING',0,now(),now()),
                       (1,'OTHER','MANUAL','기타','2026-10-01','PENDING',0,now(),now()),
                       (1,'AI_GOAL_TASK','LEGACY_UNKNOWN','목표','2026-10-01','PENDING',0,now(),now())
                """);
        jdbc.update("""
                insert into schedule (user_id,type,title,start_at,end_at,all_day,created_at,updated_at)
                values (1,'FIXED','고정','2026-10-01 09:00:00','2026-10-01 10:00:00',false,now(),now()),
                       (1,'PERSONAL','개인','2026-10-01 11:00:00','2026-10-01 12:00:00',false,now(),now())
                """);
        flyway.migrate();
        assertThat(jdbc.queryForList("select calendar_uid from todo order by todo_id", String.class))
                .containsExactly("hama-TASK-1@hama.app", "hama-OTHER-2@hama.app", "hama-AI_GOAL-3@hama.app");
        assertThat(jdbc.queryForList("select calendar_uid from schedule order by schedule_id", String.class))
                .containsExactly("hama-FIXED-1@hama.app", "hama-PERSONAL-2@hama.app");
        assertThat(jdbc.queryForList("select category from todo order by todo_id", String.class))
                .isEqualTo(List.of("TASK", "OTHER", "AI_GOAL_TASK"));
    }
}
