package com.hama.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.calendar.dto.CalendarType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private final IcsWriter writer = new IcsWriter(Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void UTF8줄은_75바이트를_넘지않고_접기를_풀면_원래문자를_유지한다() {
        String title = "한글🙂".repeat(80);
        String raw = file(title, null);
        for (String line : raw.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
            assertThat(line).doesNotContain("�");
        }
        assertThat(raw.replace("\r\n ", "")).contains("SUMMARY:" + title + "\r\n");
        assertThat(raw.replace("\r\n", "")).doesNotContain("\n", "\r");
    }

    @Test
    void 제목과_메모의_개행과_구분자를_이스케이프해_새_이벤트_주입을_막는다() {
        String raw = file("회의,팀;실\\험\r\nBEGIN:VEVENT", "첫줄\r둘째\nEND:VCALENDAR\u0000");
        String unfolded = raw.replace("\r\n ", "");
        assertThat(unfolded).contains("SUMMARY:회의\\,팀\\;실\\\\험\\nBEGIN:VEVENT\r\n",
                "DESCRIPTION:첫줄\\n둘째\\nEND:VCALENDAR\r\n");
        assertThat(unfolded.split("\r\nBEGIN:VEVENT\r\n", -1)).hasSize(2);
        assertThat(unfolded).doesNotContain("\u0000");
    }

    @Test
    void KST의_원래시각_반복규칙_시간대정의와_UTC생성시각을_보존한다() {
        String raw = file("반복", "메모").replace("\r\n ", "");
        assertThat(raw).contains("BEGIN:VTIMEZONE\r\nTZID:Asia/Seoul", "TZOFFSETTO:+0900",
                "DTSTAMP:20261003T120000Z", "DTSTART;TZID=Asia/Seoul:20261005T003000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=3", "UID:hama-FIXED-42@hama.app");
    }

    @Test
    void 빈_결과도_정상_캘린더_파일이다() {
        String raw = new String(writer.write(List.of()), StandardCharsets.UTF_8);
        assertThat(raw).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(raw).doesNotContain("VEVENT").contains("BEGIN:VTIMEZONE", "TZID:Asia/Seoul");
    }

    private String file(String title, String memo) {
        return new String(writer.write(List.of(new CalendarEvent(CalendarType.FIXED, 42L, title,
                LocalDateTime.of(2026, 10, 5, 0, 30), LocalDateTime.of(2026, 10, 5, 1, 30), false,
                "FREQ=WEEKLY;BYDAY=MO;COUNT=3", memo))), StandardCharsets.UTF_8);
    }
}
