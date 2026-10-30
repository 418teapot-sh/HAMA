package com.hama.domain.schedule;

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
class ScheduleCompletionMigrationIntegrationTest {
    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void V29_기존일정은_그대로두고_V30에서_완료기록을_추가한다() {
        assertThat(jdbc.queryForObject("select database()", String.class)).isEqualTo("hama_test");
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("29").load();
        previous.clean();
        previous.migrate();
        jdbc.update("""
                insert into schedule (calendar_uid, user_id, type, title, start_at, end_at, all_day, created_at, updated_at)
                values ('hama-PERSONAL-1@hama.app', 1, 'PERSONAL', '기존 일정',
                        '2026-10-03 09:00:00', '2026-10-03 10:00:00', false, now(), now())
                """);
        var before = jdbc.queryForMap("select * from schedule where schedule_id=1");
        flyway.migrate();
        assertThat(jdbc.queryForMap("select * from schedule where schedule_id=1")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from schedule_completion", Integer.class)).isZero();
    }
}
