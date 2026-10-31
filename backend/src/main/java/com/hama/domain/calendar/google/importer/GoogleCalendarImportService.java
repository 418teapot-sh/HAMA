package com.hama.domain.calendar.google.importer;

import com.hama.domain.calendar.google.importer.GoogleCalendarImportResult.SkipReason;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import com.hama.domain.shared.time.Be3Time;
import com.hama.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 외부 조회·검증은 DB 트랜잭션 밖에서 끝내고 유효한 개인 일정만 한 번에 저장합니다. */
@Service
public class GoogleCalendarImportService {
    public static final int MAX_EVENTS = 1000;
    private final GoogleEventsProvider provider;
    private final GoogleCalendarImportWriter writer;

    public GoogleCalendarImportService(GoogleEventsProvider provider, GoogleCalendarImportWriter writer) {
        this.provider = provider;
        this.writer = writer;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GoogleCalendarImportResult importEvents(Long userId, String accessToken, String canonicalCalendarId,
            LocalDate from, LocalDate to) {
        return importEvents(userId, accessToken, canonicalCalendarId, from, to, () -> {}, () -> {});
    }

    /**
     * OAuth 세션 검증/소비 콜백은 사용자 잠금을 획득한 저장 트랜잭션 안에서만 실행합니다.
     * 네트워크 호출이 필요한 캘린더 선택 권한 검사는 호출자가 이 메서드 전에 수행해야 합니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GoogleCalendarImportResult importEvents(Long userId, String accessToken, String canonicalCalendarId,
            LocalDate from, LocalDate to, Runnable lockSession, Runnable consumeSession) {
        validateRequest(userId, accessToken, canonicalCalendarId, from, to);
        List<GoogleCalendarEvent> events = provider.fetchEvents(accessToken, canonicalCalendarId, from, to);
        if (events.size() > MAX_EVENTS) throw new BusinessException(GoogleImportErrorCode.GOOGLE_IMPORT_LIMIT_EXCEEDED);
        var skipped = new EnumMap<SkipReason, Integer>(SkipReason.class);
        var candidates = new ArrayList<Candidate>();
        for (GoogleCalendarEvent event : events) {
            try {
                candidates.add(convert(userId, event, from, to));
            } catch (SkippedEvent e) {
                skipped.merge(e.reason, 1, Integer::sum);
            }
        }
        var counts = writer.write(userId, hash(canonicalCalendarId), candidates, lockSession, consumeSession);
        return new GoogleCalendarImportResult(counts.importedCount(), counts.duplicateCount(),
                skipped.values().stream().mapToInt(Integer::intValue).sum(), skipped);
    }

    private static Candidate convert(Long userId, GoogleCalendarEvent event, LocalDate from, LocalDate to) {
        if (event == null || event.id() == null || event.id().isBlank()) throw skipped(SkipReason.MISSING_EVENT_ID);
        if ("cancelled".equals(event.status())) throw skipped(SkipReason.CANCELLED);
        if (event.summary() == null || event.summary().isBlank() || event.summary().length() > 100) {
            throw skipped(SkipReason.INVALID_TITLE);
        }
        if (event.description() != null && event.description().getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw skipped(SkipReason.MEMO_TOO_LONG);
        }
        LocalDateTime start;
        LocalDateTime end;
        boolean allDay;
        try {
            if (event.start() == null || event.end() == null) throw skipped(SkipReason.INVALID_TIME);
            allDay = event.start().date() != null;
            if (allDay) {
                if (event.start().dateTime() != null || event.end().date() == null || event.end().dateTime() != null) {
                    throw skipped(SkipReason.INVALID_TIME);
                }
                start = LocalDate.parse(event.start().date()).atStartOfDay();
                end = LocalDate.parse(event.end().date()).atStartOfDay();
            } else {
                if (event.end().date() != null) throw skipped(SkipReason.INVALID_TIME);
                start = timedKst(event.start());
                end = timedKst(event.end());
            }
        } catch (DateTimeException | NullPointerException e) {
            throw skipped(SkipReason.INVALID_TIME);
        }
        Schedule schedule;
        try {
            schedule = Schedule.create(userId, ScheduleType.PERSONAL, event.summary(), start, end,
                    allDay, null, event.description());
        } catch (BusinessException e) {
            throw skipped(SkipReason.INVALID_TIME);
        }
        if (!end.isAfter(from.atStartOfDay()) || !start.isBefore(to.plusDays(1).atStartOfDay())) {
            throw skipped(SkipReason.OUTSIDE_RANGE);
        }
        return new Candidate(hash(event.id()), schedule);
    }

    private static LocalDateTime timedKst(GoogleCalendarEvent.EventTime time) {
        try {
            return OffsetDateTime.parse(time.dateTime()).atZoneSameInstant(Be3Time.KST).toLocalDateTime();
        } catch (DateTimeParseException noOffset) {
            if (time.timeZone() == null || time.timeZone().isBlank()) throw noOffset;
            LocalDateTime local = LocalDateTime.parse(time.dateTime());
            ZoneId zone = ZoneId.of(time.timeZone());
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) throw noOffset; // DST 겹침·빈 시간은 임의 보정하지 않습니다.
            return local.atOffset(offsets.getFirst()).atZoneSameInstant(Be3Time.KST).toLocalDateTime();
        }
    }

    public static void validateRequest(Long userId, String accessToken, String calendarId, LocalDate from, LocalDate to) {
        if (userId == null || accessToken == null || accessToken.isBlank() || calendarId == null || calendarId.isBlank()
                || "primary".equals(calendarId) || from == null || to == null || from.getYear() < 1000
                || to.getYear() > 9999 || to.equals(LocalDate.of(9999, 12, 31))
                || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new BusinessException(GoogleImportErrorCode.GOOGLE_IMPORT_INVALID_INPUT);
        }
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    record Candidate(String eventHash, Schedule schedule) {}

    private static SkippedEvent skipped(SkipReason reason) { return new SkippedEvent(reason); }
    private static class SkippedEvent extends RuntimeException {
        private final SkipReason reason;
        SkippedEvent(SkipReason reason) { this.reason = reason; }
    }
}
