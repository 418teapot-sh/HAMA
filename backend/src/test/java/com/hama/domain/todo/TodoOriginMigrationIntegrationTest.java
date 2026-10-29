package com.hama.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import java.sql.SQLException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TodoOriginMigrationIntegrationTest {
    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void V27_기존데이터를_보존하며_자동이름에_의존하지않고_분류와_출처만_확장한다() {
        // 이 테스트도 application-test.yml의 전용 hama_test 외에는 실행하지 않습니다.
        assertThat(jdbc.queryForObject("select database()", String.class)).isEqualTo("hama_test");
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("27").load();
        previous.clean();
        previous.migrate();
        jdbc.update("""
                insert into todo (user_id,category,content,todo_date,status,postponed_count,status_note,created_at,updated_at)
                values (1,'TASK','일반','2026-10-01','PENDING',2,'메모',now(),now()),
                       (1,'AI_GOAL_TASK','기존 목표','2026-10-01','COMPLETED',3,'기존 메모',now(),now())
                """);
        jdbc.execute("alter table todo drop check todo_chk_1");
        jdbc.execute("alter table todo add constraint renamed_category_guard check (category in ('TASK','AI_GOAL_TASK'))");
        flyway.migrate();
        assertThat(jdbc.queryForList("select origin from todo order by todo_id", String.class))
                .containsExactly("MANUAL", "LEGACY_UNKNOWN");
        assertThat(jdbc.queryForList("select postponed_count from todo order by todo_id", Integer.class)).containsExactly(2, 3);
        assertThat(jdbc.queryForList("select status_note from todo order by todo_id", String.class)).containsExactly("메모", "기존 메모");
        jdbc.update("update todo set category='OTHER' where category='TASK'");
        for (String column : java.util.List.of("status", "category", "origin")) {
            DataAccessException failure = assertThrows(DataAccessException.class,
                    () -> jdbc.update("update todo set " + column + "='INVALID'"));
            assertThat(failure.getCause()).isInstanceOf(SQLException.class);
            assertThat(((SQLException) failure.getCause()).getErrorCode()).isEqualTo(3819);
        }
    }
}
