package com.hama.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.hama.domain.calendar.service.ScheduleOccurrences.Occurrence;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneOffsetTransition;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleOccurrencesTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private final ScheduleOccurrences occurrences = new ScheduleOccurrences();

    @Test
    void 비반복은_기간교차로_선택하고_종료경계를_포함하지_않는다() {
        Schedule schedule = timed("2026-10-01T23:00:00", "2026-10-03T00:00:00", null);
        assertThat(between(schedule, "2026-10-02", "2026-10-02"))
                .containsExactly(occurrence("2026-10-01T23:00:00", "2026-10-03T00:00:00"));
        assertThat(between(schedule, "2026-10-03", "2026-10-03")).isEmpty();
        assertThat(between(schedule, "2026-09-30", "2026-09-30")).isEmpty();
        assertThat(occurrences.overlaps(schedule, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2))).isTrue();
        assertThat(occurrences.overlaps(schedule, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3))).isFalse();
    }

    @Test
    void 일간_간격과_COUNT는_조회시작이_아닌_원래_DTSTART부터_센다() {
        Schedule schedule = timed("2026-10-01T09:00:00", "2026-10-01T10:00:00", "FREQ=DAILY;INTERVAL=2;COUNT=3");
        assertThat(between(schedule, "2026-10-02", "2026-10-10"))
                .containsExactly(occurrence("2026-10-03T09:00:00", "2026-10-03T10:00:00"),
                        occurrence("2026-10-05T09:00:00", "2026-10-05T10:00:00"));
        assertThat(between(schedule, "2026-10-06", "2026-10-10")).isEmpty();
        assertThat(between(schedule, "2026-09-01", "2026-09-30")).isEmpty();
    }

    @Test
    void 수요일에_시작한_첫주의_지난_월요일은_COUNT에_포함하지_않는다() {
        Schedule schedule = timed("2026-10-07T09:00:00", "2026-10-07T10:00:00",
                "FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=4");
        assertThat(starts(between(schedule, "2026-10-01", "2026-10-31")))
                .containsExactly(LocalDateTime.parse("2026-10-07T09:00:00"), LocalDateTime.parse("2026-10-09T09:00:00"),
                        LocalDateTime.parse("2026-10-12T09:00:00"), LocalDateTime.parse("2026-10-14T09:00:00"));
        assertThat(starts(between(schedule, "2026-10-12", "2026-10-31")))
                .containsExactly(LocalDateTime.parse("2026-10-12T09:00:00"), LocalDateTime.parse("2026-10-14T09:00:00"));
    }

    @Test
    void 주간_간격은_월요일_기준이고_BYDAY는_중복없이_날짜순이다() {
        Schedule schedule = timed("2026-10-07T09:00:00", "2026-10-07T10:00:00",
                "FREQ=WEEKLY;INTERVAL=2;BYDAY=FR,MO,WE,MO;COUNT=4");
        assertThat(starts(between(schedule, "2026-10-01", "2026-10-31")))
                .containsExactly(LocalDateTime.parse("2026-10-07T09:00:00"), LocalDateTime.parse("2026-10-09T09:00:00"),
                        LocalDateTime.parse("2026-10-19T09:00:00"), LocalDateTime.parse("2026-10-21T09:00:00"));
        assertThat(between(schedule, "2026-10-10", "2026-10-18")).isEmpty();
        Schedule sunday = timed("2026-10-04T09:00:00", "2026-10-04T10:00:00",
                "FREQ=WEEKLY;INTERVAL=2;BYDAY=SU,MO;COUNT=3");
        assertThat(starts(between(sunday, "2026-10-01", "2026-10-31")))
                .containsExactly(LocalDateTime.parse("2026-10-04T09:00:00"), LocalDateTime.parse("2026-10-12T09:00:00"),
                        LocalDateTime.parse("2026-10-18T09:00:00"));
    }

    @Test
    void BYDAY_생략은_시작요일이며_COUNT_하나는_첫_일정만_반환한다() {
        Schedule schedule = timed("2026-10-07T09:00:00", "2026-10-07T10:00:00", "FREQ=WEEKLY;COUNT=2");
        assertThat(starts(between(schedule, "2026-10-01", "2026-10-31")))
                .containsExactly(LocalDateTime.parse("2026-10-07T09:00:00"), LocalDateTime.parse("2026-10-14T09:00:00"));
        Schedule one = timed("2026-10-07T09:00:00", "2026-10-07T10:00:00",
                "FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=1");
        assertThat(between(one, "2026-10-08", "2026-12-31")).isEmpty();
    }

    @Test
    void UNTIL은_발생시작의_포함경계이고_종료시각을_자르지_않는다() {
        Schedule schedule = timed("2026-10-01T09:00:00", "2026-10-01T10:00:00",
                "FREQ=DAILY;UNTIL=20261003T000000Z");
        assertThat(between(schedule, "2026-10-03", "2026-10-04"))
                .containsExactly(occurrence("2026-10-03T09:00:00", "2026-10-03T10:00:00"));
        Schedule oneSecondEarly = timed("2026-10-01T09:00:00", "2026-10-01T10:00:00",
                "FREQ=DAILY;UNTIL=20261002T235959Z");
        assertThat(between(oneSecondEarly, "2026-10-03", "2026-10-04")).isEmpty();
        Schedule allDay = allDay("2026-10-01", "2026-10-02", "FREQ=DAILY;UNTIL=20261003");
        assertThat(between(allDay, "2026-10-03", "2026-10-04"))
                .containsExactly(occurrence("2026-10-03T00:00:00", "2026-10-04T00:00:00"));
    }

    @Test
    void 여러날_반복은_조회이전에_시작한_발생도_모두_반환한다() {
        Schedule schedule = timed("2026-10-01T23:00:00", "2026-10-04T01:00:00", "FREQ=DAILY;COUNT=4");
        assertThat(between(schedule, "2026-10-04", "2026-10-04"))
                .containsExactly(occurrence("2026-10-01T23:00:00", "2026-10-04T01:00:00"),
                        occurrence("2026-10-02T23:00:00", "2026-10-05T01:00:00"),
                        occurrence("2026-10-03T23:00:00", "2026-10-06T01:00:00"),
                        occurrence("2026-10-04T23:00:00", "2026-10-07T01:00:00"));
        Schedule twoWeeks = allDay("2026-10-01", "2026-10-16", "FREQ=WEEKLY;COUNT=3");
        assertThat(starts(between(twoWeeks, "2026-10-16", "2026-10-16")))
                .containsExactly(LocalDateTime.parse("2026-10-08T00:00:00"), LocalDateTime.parse("2026-10-15T00:00:00"));
    }

    @Test
    void 오래전_DTSTART는_과거_발생을_나열하지않고_조회위치로_이동한다() {
        Schedule daily = timed("1000-01-01T09:00:00", "1000-01-01T10:00:00", "FREQ=DAILY");
        Schedule weekly = timed("1000-01-01T09:00:00", "1000-01-01T10:00:00", "FREQ=WEEKLY");
        LocalDate weeklyDate = LocalDate.of(1000, 1, 1).plusWeeks(400000);
        assertTimeout(Duration.ofSeconds(2), () -> {
            assertThat(between(daily, "9999-12-31", "9999-12-31"))
                    .containsExactly(occurrence("9999-12-31T09:00:00", "9999-12-31T10:00:00"));
            assertThat(occurrences.between(weekly, weeklyDate, weeklyDate))
                    .containsExactly(new Occurrence(weeklyDate.atTime(9, 0), weeklyDate.atTime(10, 0)));
        });
        Schedule expired = timed("1000-01-01T09:00:00", "1000-01-01T10:00:00", "FREQ=DAILY;COUNT=2");
        assertThat(between(expired, "9999-12-31", "9999-12-31")).isEmpty();
    }

    @Test
    void 최대날짜_다음날_종료와_큰_간격의_int_오버플로를_처리한다() {
        Schedule schedule = timed("9999-12-30T23:00:00", "9999-12-31T01:00:00", "FREQ=DAILY;COUNT=2");
        assertThat(between(schedule, "9999-12-31", "9999-12-31"))
                .containsExactly(occurrence("9999-12-30T23:00:00", "9999-12-31T01:00:00"),
                        new Occurrence(LocalDateTime.of(9999, 12, 31, 23, 0), LocalDateTime.of(10000, 1, 1, 1, 0)));
        for (String frequency : List.of("DAILY", "WEEKLY")) {
            Schedule largeInterval = timed("2026-10-01T09:00:00", "2026-10-01T10:00:00",
                    "FREQ=" + frequency + ";INTERVAL=2147483647;COUNT=2147483647");
            assertThat(between(largeInterval, "2026-10-01", "2026-10-31")).hasSize(1);
            assertThat(between(largeInterval, "9999-12-31", "9999-12-31")).isEmpty();
        }
    }

    @Test
    void 원본기간이_길어도_overlaps는_첫_결과에서_종료한다() {
        Schedule schedule = timed("1000-01-01T09:00:00", "9999-01-01T10:00:00", "FREQ=DAILY");
        assertTimeout(Duration.ofSeconds(2), () -> assertThat(occurrences.overlaps(schedule,
                LocalDate.of(9000, 1, 1), LocalDate.of(9000, 1, 1))).isTrue());
    }

    @Test
    void 생성된_공백시각은_건너뛰고_COUNT에서도_제외하며_조회점프후에도_보정한다() {
        ZoneOffsetTransition transition = gap();
        LocalDateTime missing = transition.getDateTimeBefore().plusSeconds(transition.getDuration().getSeconds() / 2);
        LocalDateTime start = missing.minusDays(1);
        Schedule schedule = timed(start, start.plusMinutes(15), "FREQ=DAILY;COUNT=3");
        LocalDate missingDate = missing.toLocalDate();
        assertThat(occurrences.between(schedule, missingDate, missingDate)).isEmpty();
        assertThat(occurrences.between(schedule, missingDate.plusDays(1), missingDate.plusDays(3)))
                .containsExactly(new Occurrence(missing.plusDays(1), missing.plusDays(1).plusMinutes(15)),
                        new Occurrence(missing.plusDays(2), missing.plusDays(2).plusMinutes(15)));
        assertThat(occurrences.overlaps(schedule, missingDate.plusDays(3), missingDate.plusDays(3))).isFalse();
    }

    @Test
    void 주간_COUNT도_시간대_공백의_발생을_제외한다() {
        ZoneOffsetTransition transition = gap();
        LocalDateTime missing = transition.getDateTimeBefore().plusSeconds(transition.getDuration().getSeconds() / 2);
        LocalDateTime start = missing.minusWeeks(1);
        Schedule schedule = timed(start, start.plusMinutes(15), "FREQ=WEEKLY;COUNT=3");
        assertThat(occurrences.between(schedule, missing.toLocalDate(), missing.toLocalDate().plusWeeks(3)))
                .containsExactly(new Occurrence(missing.plusWeeks(1), missing.plusWeeks(1).plusMinutes(15)),
                        new Occurrence(missing.plusWeeks(2), missing.plusWeeks(2).plusMinutes(15)));
    }

    @Test
    void 명시된_DTSTART가_공백이면_정규화하여_첫_COUNT에_포함한다() {
        ZoneOffsetTransition transition = gap();
        LocalDateTime missing = transition.getDateTimeBefore().plusSeconds(transition.getDuration().getSeconds() / 2);
        LocalDateTime normalized = missing.plus(transition.getDuration());
        Schedule schedule = timed(missing, normalized.plusMinutes(15), "FREQ=DAILY;COUNT=2");
        assertThat(occurrences.between(schedule, missing.toLocalDate(), missing.toLocalDate().plusDays(2)))
                .containsExactly(new Occurrence(normalized, normalized.plusMinutes(15)),
                        new Occurrence(missing.plusDays(1), missing.plusDays(1).plusMinutes(15)));
    }

    @Test
    void 시간대_변경일에도_DTEND의_정확한_duration을_유지한다() {
        ZoneOffsetTransition transition = gap();
        LocalDateTime onTransitionDay = transition.getDateTimeBefore().minusMinutes(30);
        LocalDateTime start = onTransitionDay.minusDays(1);
        Schedule schedule = timed(start, start.plusHours(2), "FREQ=DAILY;COUNT=2");
        assertThat(occurrences.between(schedule, onTransitionDay.toLocalDate(), onTransitionDay.toLocalDate()))
                .containsExactly(new Occurrence(onTransitionDay,
                        onTransitionDay.plusHours(2).plus(transition.getDuration())));
        Schedule dates = allDay(onTransitionDay.toLocalDate().minusDays(1).toString(),
                onTransitionDay.toLocalDate().toString(), "FREQ=DAILY;COUNT=2");
        assertThat(occurrences.between(dates, onTransitionDay.toLocalDate(), onTransitionDay.toLocalDate()))
                .containsExactly(new Occurrence(onTransitionDay.toLocalDate().atStartOfDay(),
                        onTransitionDay.toLocalDate().plusDays(1).atStartOfDay()));
    }

    @Test
    void 시간대_중복시각은_첫_offset이고_UNTIL과도_동일하게_비교한다() {
        ZoneOffsetTransition transition = KST.getRules().getTransitions().stream()
                .filter(ZoneOffsetTransition::isOverlap).reduce((first, second) -> second).orElseThrow();
        LocalDateTime ambiguous = transition.getDateTimeAfter().plusMinutes(15);
        var firstInstant = ambiguous.toInstant(transition.getOffsetBefore());
        Schedule schedule = timed(ambiguous, ambiguous.plusMinutes(15),
                "FREQ=DAILY;UNTIL=" + UNTIL.format(firstInstant));
        assertThat(occurrences.between(schedule, ambiguous.toLocalDate(), ambiguous.toLocalDate().plusDays(1)))
                .containsExactly(new Occurrence(ambiguous, ambiguous.plusMinutes(15)));
    }

    @Test
    void 자정전환으로_정규화된_DTSTART도_조회에서_누락하지_않는다() {
        ZoneOffsetTransition transition = KST.getRules().getTransitions().stream()
                .filter(ZoneOffsetTransition::isGap)
                .filter(value -> value.getDateTimeBefore().getHour() == 0)
                .findFirst().orElseThrow();
        LocalDateTime missing = transition.getDateTimeBefore().plusSeconds(transition.getDuration().getSeconds() / 2);
        LocalDateTime normalized = missing.plus(transition.getDuration());
        Schedule schedule = timed(missing, normalized.plusMinutes(15), null);
        assertThat(occurrences.between(schedule, missing.toLocalDate(), missing.toLocalDate()))
                .containsExactly(new Occurrence(normalized, normalized.plusMinutes(15)));
    }

    @Test
    void 삭제된_일정은_조회와_내보내기_선택에서_제외한다() {
        Schedule schedule = timed("2026-10-01T09:00:00", "2026-10-01T10:00:00", "FREQ=DAILY");
        schedule.delete(LocalDateTime.of(2026, 10, 2, 0, 0));
        assertThat(between(schedule, "2026-10-01", "2026-10-31")).isEmpty();
        assertThat(occurrences.overlaps(schedule, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).isFalse();
    }

    private ZoneOffsetTransition gap() {
        return KST.getRules().getTransitions().stream()
                .filter(ZoneOffsetTransition::isGap).reduce((first, second) -> second).orElseThrow();
    }

    private Schedule timed(String start, String end, String rule) {
        return timed(LocalDateTime.parse(start), LocalDateTime.parse(end), rule);
    }

    private Schedule timed(LocalDateTime start, LocalDateTime end, String rule) {
        return Schedule.create(1L, ScheduleType.FIXED, "반복 일정", start, end, false, rule, null);
    }

    private Schedule allDay(String from, String endExclusive, String rule) {
        return Schedule.create(1L, ScheduleType.PERSONAL, "종일 일정", LocalDate.parse(from).atStartOfDay(),
                LocalDate.parse(endExclusive).atStartOfDay(), true, rule, null);
    }

    private List<Occurrence> between(Schedule schedule, String from, String to) {
        return occurrences.between(schedule, LocalDate.parse(from), LocalDate.parse(to));
    }

    private Occurrence occurrence(String start, String end) {
        return new Occurrence(LocalDateTime.parse(start), LocalDateTime.parse(end));
    }

    private List<LocalDateTime> starts(List<Occurrence> values) {
        return values.stream().map(Occurrence::startAt).toList();
    }
}
