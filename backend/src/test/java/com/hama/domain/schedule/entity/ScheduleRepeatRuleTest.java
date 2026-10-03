package com.hama.domain.schedule.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.domain.schedule.exception.ScheduleErrorCode;
import com.hama.global.exception.BusinessException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScheduleRepeatRuleTest {

    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Test
    void 기본_간격과_시작요일을_적용하고_종료가_없으면_무기한이다() {
        ScheduleRepeatRule daily = ScheduleRepeatRule.parse("FREQ=DAILY", MONDAY, false);
        assertThat(daily.getInterval()).isOne();
        assertThat(daily.getCount()).isNull();
        assertThat(daily.getUntilDate()).isNull();
        assertThat(daily.getUntilInstant()).isNull();
        assertThat(daily.getByDays()).isEmpty();
        ScheduleRepeatRule weekly = ScheduleRepeatRule.parse("FREQ=WEEKLY", MONDAY, false);
        assertThat(weekly.getByDays()).containsExactly(DayOfWeek.MONDAY);
    }

    @Test
    void 순서와_대소문자에_관계없이_간격과_횟수와_주간요일을_파싱한다() {
        ScheduleRepeatRule rule = ScheduleRepeatRule.parse(
                "byday=fr,mo;count=6;freq=weekly;interval=2", MONDAY, false);
        assertThat(rule.getFrequency()).isEqualTo(ScheduleRepeatRule.Frequency.WEEKLY);
        assertThat(rule.getInterval()).isEqualTo(2);
        assertThat(rule.getCount()).isEqualTo(6);
        assertThat(rule.getByDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThatThrownBy(() -> rule.getByDays().add(DayOfWeek.TUESDAY))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 종일은_날짜_시간일정은_UTC_UNTIL이고_종료경계는_포함한다() {
        ScheduleRepeatRule allDay = ScheduleRepeatRule.parse("FREQ=DAILY;UNTIL=20261005",
                MONDAY.toLocalDate().atStartOfDay(), true);
        assertThat(allDay.getUntilDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        ScheduleRepeatRule timed = ScheduleRepeatRule.parse("FREQ=DAILY;UNTIL=20261005T000000Z", MONDAY, false);
        assertThat(timed.getUntilInstant()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "FREQ=DAıLY;COUNT=2", "FREQ=MONTHLY", "FREQ=DAILY;INTERVAL=0", "FREQ=DAILY;COUNT=0",
            "FREQ=DAILY;INTERVAL=-1", "FREQ=DAILY;INTERVAL=2147483648", "FREQ=DAILY;COUNT=1.5",
            "FREQ=DAILY;COUNT=1;UNTIL=20261006T000000Z", "COUNT=3",
            "FREQ=DAILY;FREQ=WEEKLY", "FREQ=DAILY;INTERVAL=1;INTERVAL=2",
            "FREQ=WEEKLY;BYDAY=1MO", "FREQ=WEEKLY;BYDAY=MO,", "FREQ=WEEKLY;BYDAY=XX",
            "FREQ=DAILY;BYDAY=MO", "FREQ=DAILY;BYHOUR=9", "FREQ=WEEKLY;WKST=SU",
            "FREQ=DAILY;", "FREQ=DAILY;;COUNT=2", "FREQ=DAILY;COUNT=", "FREQ=DAILY; COUNT=2",
            "FREQ=DAILY\r\nBEGIN:VEVENT", "RRULE:FREQ=DAILY",
            "FREQ=DAILY;UNTIL=20261006", "FREQ=DAILY;UNTIL=20261006T090000",
            "FREQ=DAILY;UNTIL=20260230T090000Z", "FREQ=DAILY;UNTIL=20261006T240000Z"
    })
    void 지원밖_옵션이나_잘못된_문법을_무시하지_않는다(String source) {
        assertThatThrownBy(() -> ScheduleRepeatRule.parse(source, MONDAY, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_INVALID_REPEAT_RULE));
    }

    @Test
    void 종일_UNTIL에_일시나_유효하지않은_날짜를_허용하지_않는다() {
        for (String until : java.util.List.of("20261006T000000Z", "20260230", "00001006")) {
            assertThatThrownBy(() -> ScheduleRepeatRule.parse("FREQ=DAILY;UNTIL=" + until, MONDAY, true))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void 시작요일_불일치와_시작보다_이른_반복종료를_거절한다() {
        for (String rule : java.util.List.of("FREQ=WEEKLY;BYDAY=TU,FR", "FREQ=DAILY;UNTIL=20261004T235959Z")) {
            assertThatThrownBy(() -> ScheduleRepeatRule.parse(rule, MONDAY, false))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode())
                                    .isEqualTo(ScheduleErrorCode.SCHEDULE_REPEAT_START_MISMATCH));
        }
        assertThatThrownBy(() -> ScheduleRepeatRule.parse("FREQ=DAILY;UNTIL=20261004", MONDAY, true))
                .isInstanceOf(BusinessException.class);
    }
}
