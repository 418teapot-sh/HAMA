package com.hama.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.calendar.dto.CalendarType;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleRepeatRule;
import com.hama.domain.schedule.entity.ScheduleType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Test
    void UTF8의_1부터_4바이트_문자를_경계에서_나누지_않는다() {
        String title = "aé한🙂".repeat(40);
        String raw = file(title, null);
        for (String line : raw.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
            assertThat(line).doesNotContain("�");
        }
        assertThat(raw.replace("\r\n ", "")).contains("SUMMARY:" + title + "\r\n");
    }

    @Test
    void 짝없는_surrogate는_기존_UTF8_치환과_같이_1바이트로_센다() {
        String title = "a".repeat(66) + "\ud800" + "b".repeat(74) + "\udc00";
        String raw = file(title, null);
        for (String line : raw.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(raw).contains("SUMMARY:" + "a".repeat(66) + "?\r\n ");
        String replaced = new String(title.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThat(raw.replace("\r\n ", "")).contains("SUMMARY:" + replaced + "\r\n");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "byday=fr,mo,mo;count=0006;freq=weekly;interval=02|FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR;COUNT=6",
            "count=3;freq=weekly|FREQ=WEEKLY;BYDAY=MO;COUNT=3",
            "interval=1;freq=daily|FREQ=DAILY",
            "until=20261005t013000z;freq=daily|FREQ=DAILY;UNTIL=20261005T013000Z"
    }, delimiter = '|')
    void 원문저장은_유지하고_동일의미의_대문자_FREQ첫순서로_내보낸다(String source, String expected) {
        LocalDateTime start = LocalDateTime.of(2026, 10, 5, 9, 0);
        Schedule schedule = Schedule.create(1L, ScheduleType.FIXED, "반복", start, start.plusHours(1),
                false, source, null);
        String raw = new String(writer.write(List.of(CalendarEvent.from(schedule))), StandardCharsets.UTF_8)
                .replace("\r\n ", "");
        assertThat(schedule.getRepeatRule()).isEqualTo(source);
        assertThat(raw).contains("RRULE:" + expected + "\r\n");
        ScheduleRepeatRule original = ScheduleRepeatRule.parse(source, start, false);
        ScheduleRepeatRule normalized = ScheduleRepeatRule.parse(expected, start, false);
        assertThat(normalized).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void 종일_UNTIL은_시간이나_UTC로_바꾸지_않는다() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 5, 0, 0);
        String raw = new String(writer.write(List.of(new CalendarEvent(CalendarType.PERSONAL, 7L, "종일",
                start, start.plusDays(1), true, "until=20261031;freq=daily;interval=02", null))), StandardCharsets.UTF_8);
        assertThat(raw).contains("DTSTART;VALUE=DATE:20261005", "DTEND;VALUE=DATE:20261006",
                "RRULE:FREQ=DAILY;INTERVAL=2;UNTIL=20261031\r\n");
        assertThat(raw).doesNotContain("VTIMEZONE");
    }

    @Test
    void 재사용하는_시간대정의는_초단위_과거offset과_서머타임을_보존한다() {
        String raw = file("시간대", null);
        assertThat(raw).contains("DTSTART:10000101T000000\r\nTZOFFSETFROM:+082752\r\nTZOFFSETTO:+082752",
                "DTSTART:19080401T000000\r\nTZOFFSETFROM:+082752\r\nTZOFFSETTO:+0830",
                "BEGIN:DAYLIGHT\r\nDTSTART:19880508T020000\r\nTZOFFSETFROM:+0900\r\nTZOFFSETTO:+1000",
                "BEGIN:STANDARD\r\nDTSTART:19881009T030000\r\nTZOFFSETFROM:+1000\r\nTZOFFSETTO:+0900");
        String empty = new String(writer.write(List.of()), StandardCharsets.UTF_8);
        assertThat(timeZoneBlock(empty)).isEqualTo(timeZoneBlock(raw));
    }

    private String timeZoneBlock(String raw) {
        int begin = raw.indexOf("BEGIN:VTIMEZONE");
        int end = raw.indexOf("END:VTIMEZONE\r\n") + "END:VTIMEZONE\r\n".length();
        return raw.substring(begin, end);
    }

    private String file(String title, String memo) {
        return new String(writer.write(List.of(new CalendarEvent(CalendarType.FIXED, 42L, title,
                LocalDateTime.of(2026, 10, 5, 0, 30), LocalDateTime.of(2026, 10, 5, 1, 30), false,
                "FREQ=WEEKLY;BYDAY=MO;COUNT=3", memo))), StandardCharsets.UTF_8);
    }
}
