package com.hama.domain.schedule.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.hama.domain.shared.time.Be3Time;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ScheduleRecurrenceTest {
    private final ScheduleRecurrence engine = new ScheduleRecurrence();

    @Test
    void 다양한_작은_유한규칙의_마지막종료는_실제발생_열거결과와_같다() {
        Random random = new Random(34);
        for (int i = 0; i < 400; i++) {
            boolean allDay = i % 2 == 0;
            LocalDateTime start = LocalDateTime.of(1988, 4, 1, allDay ? 0 : 2, allDay ? 0 : 30).plusDays(random.nextInt(220));
            LocalDateTime end = start.plusDays(1 + random.nextInt(8));
            String rule = i % 3 == 0 ? "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU" : "FREQ=DAILY";
            rule += ";INTERVAL=" + (1 + random.nextInt(5));
            if (i % 4 < 2) {
                rule += ";COUNT=" + (1 + random.nextInt(30));
            } else if (allDay) {
                rule += ";UNTIL=" + start.plusDays(1 + random.nextInt(120)).format(DateTimeFormatter.BASIC_ISO_DATE);
            } else {
                rule += ";UNTIL=" + DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
                        .format(start.plusDays(1 + random.nextInt(120)).atZone(Be3Time.KST).toInstant());
            }
            ScheduleRecurrence.Source source = new ScheduleRecurrence.Source(start, end, allDay, rule);
            var occurrences = engine.between(source, start.toLocalDate(), start.toLocalDate().plusYears(4));
            long expected = occurrences.getLast().endAt().atZone(Be3Time.KST).toEpochSecond();
            assertThat(engine.lastEndEpochSecond(source)).as("%s %s", start, rule).isEqualTo(expected);
        }
    }

    @Test
    void 부분첫주와_주간요일의_COUNT를_정확히_계산한다() {
        assertEnd("2026-10-07T09:00:00", "2026-10-07T10:00:00",
                "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;COUNT=4", "2026-10-21T10:00:00");
    }

    @Test
    void overlap의_두번째offset_UNTIL에서도_첫offset_발생을_포함한다() {
        var source = new ScheduleRecurrence.Source(LocalDateTime.parse("1988-10-08T02:45:00"),
                LocalDateTime.parse("1988-10-08T03:00:00"), false, "FREQ=DAILY;UNTIL=19881008T173000Z");
        // 첫 offset의 02:45 + 15분은 되돌린 시계의 두 번째 02:00입니다.
        assertThat(engine.lastEndEpochSecond(source)).isEqualTo(java.time.Instant.parse("1988-10-08T17:00:00Z").getEpochSecond());
    }

    @Test
    void COUNT는_생성된_gap을_건너뛰지만_명시된_gap시작은_센다() {
        assertEnd("1988-05-07T02:30:00", "1988-05-07T02:45:00", "FREQ=DAILY;COUNT=3", "1988-05-10T02:45:00");
        assertEnd("1988-05-08T02:30:00", "1988-05-08T03:45:00", "FREQ=DAILY;COUNT=1", "1988-05-08T03:45:00");
    }

    @Test
    void 큰_COUNT와_INTERVAL은_전개와_오버플로_없이_지원기간밖으로_표시한다() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            for (String frequency : new String[]{"DAILY", "WEEKLY"}) {
                var source = new ScheduleRecurrence.Source(LocalDateTime.parse("1000-01-01T09:00:00"),
                        LocalDateTime.parse("1000-01-01T10:00:00"), false,
                        "FREQ=" + frequency + ";COUNT=2147483647;INTERVAL=2147483647");
                assertThat(engine.lastEndEpochSecond(source)).isNull();
            }
        });
    }

    @Test
    void 최소연도_UTC와_종료없는반복과_최대날짜를_처리한다() {
        assertEnd("1000-01-01T00:00:00", "1000-01-01T00:30:00", null, "1000-01-01T00:30:00");
        assertEnd("9999-12-31T09:00:00", "9999-12-31T10:00:00", "FREQ=DAILY;COUNT=1", "9999-12-31T10:00:00");
        assertThat(engine.lastEndEpochSecond(new ScheduleRecurrence.Source(LocalDateTime.parse("2026-10-01T00:00:00"),
                LocalDateTime.parse("2026-10-02T00:00:00"), true, "FREQ=DAILY"))).isNull();
    }

    private void assertEnd(String start, String end, String rule, String expected) {
        assertThat(engine.lastEndEpochSecond(new ScheduleRecurrence.Source(LocalDateTime.parse(start),
                LocalDateTime.parse(end), false, rule))).isEqualTo(LocalDateTime.parse(expected).atZone(Be3Time.KST).toEpochSecond());
    }
}
