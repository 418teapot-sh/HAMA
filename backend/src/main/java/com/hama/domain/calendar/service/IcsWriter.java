package com.hama.domain.calendar.service;

import static com.hama.domain.shared.time.Be3Time.KST;

import com.hama.domain.schedule.entity.ScheduleRepeatRule;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** RFC 5545의 VEVENT, TEXT escaping, UTF-8 75-octet folding을 사용합니다. */
@Component
public class IcsWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String TIME_ZONE = timeZone();
    private final Clock clock;

    public IcsWriter(@Qualifier("be3Clock") Clock clock) {
        this.clock = clock;
    }

    public byte[] write(List<CalendarEvent> events) {
        StringBuilder output = new StringBuilder();
        line(output, "BEGIN:VCALENDAR");
        line(output, "VERSION:2.0");
        line(output, "PRODID:-//HAMA//Calendar//KO");
        line(output, "CALSCALE:GREGORIAN");
        // 빈 기간도 RFC 5545가 요구하는 컴포넌트를 갖도록 시간대만 포함합니다.
        if (events.isEmpty() || events.stream().anyMatch(event -> !event.allDay())) {
            output.append(TIME_ZONE);
        }
        String stamp = UTC.format(clock.instant());
        for (CalendarEvent event : events) {
            line(output, "BEGIN:VEVENT");
            line(output, "UID:hama-" + event.type() + "-" + event.refId() + "@hama.app");
            line(output, "DTSTAMP:" + stamp);
            line(output, "SUMMARY:" + text(event.title()));
            if (event.allDay()) {
                line(output, "DTSTART;VALUE=DATE:" + DATE.format(event.startAt()));
                if (event.endAt().getYear() <= 9999) {
                    line(output, "DTEND;VALUE=DATE:" + DATE.format(event.endAt()));
                } else {
                    // 9999-12-31의 하루 TASK도 4자리 DATE 범위를 벗어나지 않게 표현합니다.
                    line(output, "DURATION:P" + ChronoUnit.DAYS.between(event.startAt(), event.endAt()) + "D");
                }
            } else {
                line(output, "DTSTART;TZID=Asia/Seoul:" + DATE_TIME.format(event.startAt()));
                line(output, "DTEND;TZID=Asia/Seoul:" + DATE_TIME.format(event.endAt()));
            }
            if (event.repeatRule() != null) {
                ScheduleRepeatRule rule = ScheduleRepeatRule.parse(event.repeatRule(), event.startAt(), event.allDay());
                line(output, "RRULE:" + rule.toIcsRule());
            }
            if (event.memo() != null) {
                line(output, "DESCRIPTION:" + text(event.memo()));
            }
            line(output, "END:VEVENT");
        }
        line(output, "END:VCALENDAR");
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String timeZone() {
        StringBuilder output = new StringBuilder();
        ZoneRules rules = KST.getRules();
        line(output, "BEGIN:VTIMEZONE");
        line(output, "TZID:Asia/Seoul");
        // 지원 날짜 범위 전체와 종료 없는 반복을 위해 과거 offset 변경도 포함합니다.
        ZoneOffset initial = rules.getOffset(LocalDateTime.of(1000, 1, 1, 0, 0));
        observance(output, "STANDARD", LocalDateTime.of(1000, 1, 1, 0, 0), initial, initial);
        for (ZoneOffsetTransition transition : rules.getTransitions()) {
            String type = rules.isDaylightSavings(transition.getInstant()) ? "DAYLIGHT" : "STANDARD";
            observance(output, type, transition.getDateTimeBefore(), transition.getOffsetBefore(), transition.getOffsetAfter());
        }
        line(output, "END:VTIMEZONE");
        return output.toString();
    }

    private static void observance(StringBuilder output, String type, LocalDateTime onset,
            ZoneOffset from, ZoneOffset to) {
        line(output, "BEGIN:" + type);
        line(output, "DTSTART:" + DATE_TIME.format(onset));
        line(output, "TZOFFSETFROM:" + offset(from));
        line(output, "TZOFFSETTO:" + offset(to));
        line(output, "END:" + type);
    }

    private static String offset(ZoneOffset offset) {
        int seconds = Math.abs(offset.getTotalSeconds());
        String value = String.format(Locale.ROOT, "%s%02d%02d", offset.getTotalSeconds() < 0 ? "-" : "+",
                seconds / 3600, seconds % 3600 / 60);
        return seconds % 60 == 0 ? value : value + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    private static String text(String source) {
        String value = source.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder escaped = new StringBuilder();
        value.codePoints().forEach(code -> {
            switch (code) {
                case '\\' -> escaped.append("\\\\");
                case ';' -> escaped.append("\\;");
                case ',' -> escaped.append("\\,");
                case '\n' -> escaped.append("\\n");
                default -> {
                    // TEXT가 표현할 수 없는 제어 문자는 내보내기에서 제외합니다(HTAB은 허용).
                    if (code == '\t' || !Character.isISOControl(code)) {
                        escaped.appendCodePoint(code);
                    }
                }
            }
        });
        return escaped.toString();
    }

    private static void line(StringBuilder output, String value) {
        int octets = 0;
        for (int index = 0; index < value.length();) {
            int code = value.codePointAt(index);
            // 짝없는 surrogate는 기존 getBytes와 같이 UTF-8의 '?' 한 바이트로 치환됩니다.
            int length = code <= 0x7f || (code >= 0xd800 && code <= 0xdfff) ? 1
                    : code <= 0x7ff ? 2 : code <= 0xffff ? 3 : 4;
            if (octets + length > 75) {
                output.append("\r\n ");
                octets = 1;
            }
            output.appendCodePoint(code);
            octets += length;
            index += Character.charCount(code);
        }
        output.append("\r\n");
    }
}
