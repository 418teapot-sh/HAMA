package com.hama.domain.schedule.entity;

import static com.hama.domain.shared.time.Be3Time.KST;

import com.hama.domain.schedule.exception.ScheduleErrorCode;
import com.hama.global.exception.BusinessException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * 데모에서 지원하는 RFC 5545 3.3.10의 부분집합입니다. 원문은 엔티티에 그대로 보존합니다.
 * 종일 UNTIL은 DATE, Asia/Seoul 시간 일정의 UNTIL은 UTC DATE-TIME입니다.
 * 발생 건 전개 없이 파싱 결과를 제공하므로 캘린더 조회와 내보내기에서도 사용할 수 있습니다.
 */
@Getter
public final class ScheduleRepeatRule {

    public enum Frequency { DAILY, WEEKLY }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter UTC_DATE_TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> SUPPORTED_PARTS = Set.of("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY");
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY, "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY);

    private final Frequency frequency;
    private final int interval;
    private final Integer count;
    private final LocalDate untilDate;
    private final Instant untilInstant;
    private final Set<DayOfWeek> byDays;

    private ScheduleRepeatRule(Frequency frequency, int interval, Integer count, LocalDate untilDate,
            Instant untilInstant, Set<DayOfWeek> byDays) {
        this.frequency = frequency;
        this.interval = interval;
        this.count = count;
        this.untilDate = untilDate;
        this.untilInstant = untilInstant;
        this.byDays = Set.copyOf(byDays);
    }

    public static ScheduleRepeatRule parse(String source, LocalDateTime startAt, boolean allDay) {
        if (source == null || source.isBlank() || source.length() > 255 || startAt == null
                || !source.chars().allMatch(character -> character < 128)) {
            throw invalid();
        }
        Map<String, String> parts = new HashMap<>();
        for (String part : source.toUpperCase(Locale.ROOT).split(";", -1)) {
            String[] pair = part.split("=", -1);
            if (pair.length != 2 || !SUPPORTED_PARTS.contains(pair[0]) || pair[1].isEmpty()
                    || parts.putIfAbsent(pair[0], pair[1]) != null) {
                throw invalid();
            }
        }
        Frequency frequency;
        try {
            frequency = Frequency.valueOf(parts.getOrDefault("FREQ", ""));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        if (parts.containsKey("COUNT") && parts.containsKey("UNTIL")) {
            throw invalid();
        }
        int interval = positiveInteger(parts.getOrDefault("INTERVAL", "1"));
        Integer count = parts.containsKey("COUNT") ? positiveInteger(parts.get("COUNT")) : null;
        Set<DayOfWeek> byDays = EnumSet.noneOf(DayOfWeek.class);
        if (parts.containsKey("BYDAY")) {
            if (frequency != Frequency.WEEKLY) {
                throw invalid();
            }
            for (String day : parts.get("BYDAY").split(",", -1)) {
                DayOfWeek parsed = WEEKDAYS.get(day);
                if (parsed == null) {
                    throw invalid();
                }
                byDays.add(parsed);
            }
            // DTSTART와 RRULE이 불일치하면 RFC 5545에서 발생 집합이 정의되지 않습니다.
            if (!byDays.contains(startAt.getDayOfWeek())) {
                throw new BusinessException(ScheduleErrorCode.SCHEDULE_REPEAT_START_MISMATCH);
            }
        } else if (frequency == Frequency.WEEKLY) {
            byDays.add(startAt.getDayOfWeek());
        }
        LocalDate untilDate = null;
        Instant untilInstant = null;
        if (parts.containsKey("UNTIL")) {
            String until = parts.get("UNTIL");
            try {
                if (allDay) {
                    if (!until.matches("[0-9]{8}")) {
                        throw invalid();
                    }
                    untilDate = LocalDate.parse(until, DATE);
                    if (untilDate.getYear() < 1000) {
                        throw invalid();
                    }
                } else {
                    if (!until.matches("[0-9]{8}T[0-9]{6}Z")) {
                        throw invalid();
                    }
                    LocalDateTime utc = LocalDateTime.parse(until, UTC_DATE_TIME);
                    if (utc.getYear() < 1000) {
                        throw invalid();
                    }
                    untilInstant = utc.toInstant(ZoneOffset.UTC);
                }
            } catch (java.time.DateTimeException exception) {
                throw invalid();
            }
            if ((untilDate != null && untilDate.isBefore(startAt.toLocalDate()))
                    || (untilInstant != null && untilInstant.isBefore(startAt.atZone(KST).toInstant()))) {
                throw new BusinessException(ScheduleErrorCode.SCHEDULE_REPEAT_START_MISMATCH);
            }
        }
        return new ScheduleRepeatRule(frequency, interval, count, untilDate, untilInstant, byDays);
    }

    /** 저장 원문을 바꾸지 않고 RFC 5545에 맞게 FREQ부터 대문자로 직렬화합니다. */
    public String toIcsRule() {
        StringBuilder result = new StringBuilder("FREQ=").append(frequency);
        if (interval != 1) {
            result.append(";INTERVAL=").append(interval);
        }
        if (!byDays.isEmpty()) {
            result.append(";BYDAY=");
            boolean first = true;
            for (DayOfWeek day : DayOfWeek.values()) {
                if (byDays.contains(day)) {
                    if (!first) {
                        result.append(',');
                    }
                    result.append(day.name(), 0, 2);
                    first = false;
                }
            }
        }
        if (count != null) {
            result.append(";COUNT=").append(count);
        } else if (untilDate != null) {
            result.append(";UNTIL=").append(DATE.format(untilDate));
        } else if (untilInstant != null) {
            result.append(";UNTIL=").append(UTC_DATE_TIME.format(untilInstant.atOffset(ZoneOffset.UTC)));
        }
        return result.toString();
    }

    private static int positiveInteger(String value) {
        if (!value.matches("[0-9]+")) {
            throw invalid();
        }
        try {
            int number = Integer.parseInt(value);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException exception) {
            throw invalid();
        }
        throw invalid();
    }

    private static BusinessException invalid() {
        return new BusinessException(ScheduleErrorCode.SCHEDULE_INVALID_REPEAT_RULE);
    }
}
