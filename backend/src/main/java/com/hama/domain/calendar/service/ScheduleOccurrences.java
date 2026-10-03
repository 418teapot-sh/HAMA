package com.hama.domain.calendar.service;

import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleRepeatRule;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * 조회 구간에 걸치는 원본 발생 건을 반환합니다. 날짜별 분할과 소유권 조회는 호출자가 담당합니다.
 * RFC 5545 3.3.5, 3.3.10, 3.8.5.3에 따라 시간대·COUNT·DTEND 지속시간을 처리합니다.
 */
@Component
public final class ScheduleOccurrences {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneRules ZONE_RULES = KST.getRules();

    public record Occurrence(LocalDateTime startAt, LocalDateTime endAt) {
    }

    public List<Occurrence> between(Schedule schedule, LocalDate from, LocalDate to) {
        List<Occurrence> occurrences = new ArrayList<>();
        forEach(schedule, from, to, occurrences::add);
        return List.copyOf(occurrences);
    }

    /** 발생 전체를 중간 리스트에 모으지 않고 호출자에게 바로 전달합니다. */
    public void forEach(Schedule schedule, LocalDate from, LocalDate to, Consumer<Occurrence> consumer) {
        visit(schedule, from, to, occurrence -> {
            consumer.accept(occurrence);
            return false;
        });
    }

    /** ICS 원본 선택 시 첫 교차 발생에서 종료하므로 전체 발생 목록을 만들지 않습니다. */
    public boolean overlaps(Schedule schedule, LocalDate from, LocalDate to) {
        return visit(schedule, from, to, occurrence -> true);
    }

    private boolean visit(Schedule schedule, LocalDate from, LocalDate to, Predicate<Occurrence> visitor) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from은 to 이후일 수 없습니다.");
        }
        if (schedule.getDeletedAt() != null) {
            return false;
        }
        Expansion expansion = new Expansion(schedule, from, to);
        if (expansion.rule == null) {
            return expansion.consider(schedule.getStartAt(), 1, visitor) == Result.MATCHED;
        }
        if (expansion.rule.getFrequency() == ScheduleRepeatRule.Frequency.DAILY) {
            return daily(expansion, visitor);
        }
        return weekly(expansion, visitor);
    }

    private boolean daily(Expansion expansion, Predicate<Occurrence> visitor) {
        long interval = expansion.rule.getInterval();
        long index = Math.max(0, Math.floorDiv(
                ChronoUnit.DAYS.between(expansion.start.toLocalDate(), expansion.lowerDate), interval));
        LocalDateTime candidate = expansion.start.plusDays(index * interval);
        while (candidate.isBefore(expansion.upperLocalBound)) {
            Result result = expansion.consider(candidate, index + 1, visitor);
            if (result != Result.CONTINUE) {
                return result == Result.MATCHED;
            }
            index++;
            candidate = candidate.plusDays(interval);
        }
        return false;
    }

    private boolean weekly(Expansion expansion, Predicate<Occurrence> visitor) {
        long intervalDays = 7L * expansion.rule.getInterval();
        long cycle = Math.max(0, Math.floorDiv(
                ChronoUnit.DAYS.between(expansion.weekAnchor, expansion.lowerDate), intervalDays));
        LocalDate week = expansion.weekAnchor.plusDays(cycle * intervalDays);
        while (week.atStartOfDay().isBefore(expansion.upperLocalBound)) {
            for (int offset : expansion.weekdayOffsets) {
                LocalDateTime candidate = week.plusDays(offset).atTime(expansion.start.toLocalTime());
                if (candidate.isBefore(expansion.start)) {
                    continue;
                }
                if (!candidate.isBefore(expansion.upperLocalBound)) {
                    return false;
                }
                Result result = expansion.consider(candidate, expansion.rawOrdinal(candidate), visitor);
                if (result != Result.CONTINUE) {
                    return result == Result.MATCHED;
                }
            }
            week = week.plusDays(intervalDays);
        }
        return false;
    }

    private enum Result { CONTINUE, FINISHED, MATCHED }

    private static final class Expansion {
        private final LocalDateTime start;
        private final boolean allDay;
        private final ScheduleRepeatRule rule;
        private final Duration exactDuration;
        private final long durationDays;
        private final LocalDateTime queryStart;
        private final LocalDateTime queryEnd;
        private final Instant queryStartInstant;
        private final Instant queryEndInstant;
        private final LocalDate lowerDate;
        private final LocalDateTime upperLocalBound;
        private final LocalDate weekAnchor;
        private final List<Integer> weekdayOffsets;
        private final int firstWeekCount;
        private final List<Long> skippedOrdinals;

        private Expansion(Schedule schedule, LocalDate from, LocalDate to) {
            this.start = schedule.getStartAt();
            this.allDay = schedule.isAllDay();
            this.rule = schedule.getRepeatRule() == null ? null
                    : ScheduleRepeatRule.parse(schedule.getRepeatRule(), start, allDay);
            this.queryStart = from.atStartOfDay();
            this.queryEnd = to.plusDays(1).atStartOfDay();
            this.queryStartInstant = queryStart.atZone(KST).toInstant();
            this.queryEndInstant = queryEnd.atZone(KST).toInstant();
            this.durationDays = ChronoUnit.DAYS.between(start.toLocalDate(), schedule.getEndAt().toLocalDate());
            this.exactDuration = Duration.between(start.atZone(KST).toInstant(),
                    schedule.getEndAt().atZone(KST).toInstant());
            if (!allDay && (exactDuration.isZero() || exactDuration.isNegative())) {
                throw new IllegalStateException("저장된 시간 일정의 실제 종료가 시작보다 늦어야 합니다.");
            }
            if (allDay) {
                this.lowerDate = queryStart.minusDays(durationDays).toLocalDate();
                this.upperLocalBound = queryEnd;
            } else {
                // 모든 유효 offset의 바깥 경계로 후보를 잡고 실제 instant로 교차를 판정합니다.
                // 과거 offset 변화와 gap DTSTART의 정규화가 있어도 후보가 누락되지 않습니다.
                this.lowerDate = LocalDateTime.ofInstant(queryStartInstant.minus(exactDuration),
                        ZoneOffset.MIN).toLocalDate();
                this.upperLocalBound = LocalDateTime.ofInstant(queryEndInstant, ZoneOffset.MAX);
            }
            this.weekAnchor = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            this.weekdayOffsets = rule == null ? List.of() : rule.getByDays().stream()
                    .map(day -> day.getValue() - 1).sorted().toList();
            this.firstWeekCount = (int) weekdayOffsets.stream()
                    .filter(offset -> offset >= start.getDayOfWeek().getValue() - 1).count();
            this.skippedOrdinals = skippedGapOrdinals();
        }

        private Result consider(LocalDateTime candidate, long rawOrdinal, Predicate<Occurrence> visitor) {
            if (rule != null && rule.getCount() != null) {
                int position = Collections.binarySearch(skippedOrdinals, rawOrdinal);
                long skippedThroughCandidate = position >= 0 ? position + 1L : -position - 1L;
                if (rawOrdinal - skippedThroughCandidate > rule.getCount()) {
                    return Result.FINISHED;
                }
            }
            if (allDay) {
                if (rule != null && rule.getUntilDate() != null
                        && candidate.toLocalDate().isAfter(rule.getUntilDate())) {
                    return Result.FINISHED;
                }
                LocalDateTime end = candidate.plusDays(durationDays);
                if (candidate.isBefore(queryEnd) && end.isAfter(queryStart)
                        && visitor.test(new Occurrence(candidate, end))) {
                    return Result.MATCHED;
                }
                return Result.CONTINUE;
            }
            // DTSTART 자체는 명시된 DATE-TIME이므로 gap을 이전 offset으로 해석합니다.
            // RRULE이 새로 만든 gap 시각만 무시하며 COUNT에서도 제외합니다.
            if (!candidate.equals(start) && ZONE_RULES.getValidOffsets(candidate).isEmpty()) {
                return Result.CONTINUE;
            }
            var zonedStart = candidate.atZone(KST); // 중복 시각은 RFC의 첫 번째 offset을 사용합니다.
            Instant startInstant = zonedStart.toInstant();
            if (rule != null && rule.getUntilInstant() != null && startInstant.isAfter(rule.getUntilInstant())) {
                return Result.FINISHED;
            }
            Instant endInstant = startInstant.plus(exactDuration);
            if (startInstant.isBefore(queryEndInstant) && endInstant.isAfter(queryStartInstant)
                    && visitor.test(new Occurrence(zonedStart.toLocalDateTime(),
                            endInstant.atZone(KST).toLocalDateTime()))) {
                return Result.MATCHED;
            }
            return Result.CONTINUE;
        }

        /** 발생 날짜 전체 대신 Asia/Seoul의 유한한 과거 전환에서 공백에 속한 후보만 검사합니다. */
        private List<Long> skippedGapOrdinals() {
            if (allDay || rule == null || rule.getCount() == null) {
                return List.of();
            }
            TreeSet<Long> skipped = new TreeSet<>();
            for (ZoneOffsetTransition transition : ZONE_RULES.getTransitions()) {
                if (!transition.isGap()) {
                    continue;
                }
                LocalDateTime gapStart = transition.getDateTimeBefore();
                LocalDateTime gapEnd = transition.getDateTimeAfter();
                for (LocalDate date = gapStart.toLocalDate(); !date.isAfter(gapEnd.toLocalDate());
                        date = date.plusDays(1)) {
                    LocalDateTime candidate = date.atTime(start.toLocalTime());
                    if (!candidate.isAfter(start) || candidate.isBefore(gapStart) || !candidate.isBefore(gapEnd)) {
                        continue;
                    }
                    long ordinal = rawOrdinal(candidate);
                    if (ordinal > 0) {
                        skipped.add(ordinal);
                    }
                }
            }
            return List.copyOf(skipped);
        }

        /** DTSTART부터의 1-based 순번. 공백 제외 전의 값이며 반복 패턴 밖의 날짜는 0입니다. */
        private long rawOrdinal(LocalDateTime candidate) {
            long days = ChronoUnit.DAYS.between(start.toLocalDate(), candidate.toLocalDate());
            if (days < 0) {
                return 0;
            }
            if (rule.getFrequency() == ScheduleRepeatRule.Frequency.DAILY) {
                return days % rule.getInterval() == 0 ? days / rule.getInterval() + 1 : 0;
            }
            long weeks = ChronoUnit.DAYS.between(weekAnchor, candidate.toLocalDate()) / 7;
            if (weeks % rule.getInterval() != 0) {
                return 0;
            }
            int position = weekdayOffsets.indexOf(candidate.getDayOfWeek().getValue() - 1);
            if (position < 0) {
                return 0;
            }
            long cycle = weeks / rule.getInterval();
            if (cycle == 0) {
                return position - (weekdayOffsets.size() - firstWeekCount) + 1L;
            }
            return firstWeekCount + (cycle - 1) * weekdayOffsets.size() + position + 1;
        }
    }
}
