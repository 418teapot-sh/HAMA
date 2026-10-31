package com.hama.domain.calendar.google.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hama.domain.calendar.google.importer.GoogleCalendarEvent.EventTime;
import com.hama.domain.calendar.google.importer.GoogleCalendarImportResult.SkipReason;
import com.hama.domain.schedule.entity.ScheduleType;
import com.hama.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleCalendarImportServiceTest {
    private final GoogleEventsProvider provider = mock(GoogleEventsProvider.class);
    private final GoogleCalendarImportWriter writer = mock(GoogleCalendarImportWriter.class);
    private final GoogleCalendarImportService service = new GoogleCalendarImportService(provider, writer);
    private final LocalDate from = LocalDate.of(2026, 10, 1);
    private final LocalDate to = LocalDate.of(2026, 10, 31);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void importsExpandedInstancesAsPersonalAndPreservesAllDayExclusiveEndAndOffsets() {
        var allDay = event("all-day", "종일", new EventTime("2026-10-10", null), new EventTime("2026-10-12", null));
        var timed = event("series-instance-1", "시차", new EventTime(null, "2026-10-12T20:00:00-07:00"),
                new EventTime(null, "2026-10-12T21:00:00-07:00"));
        when(provider.fetchEvents("token", "calendar@example.com", from, to)).thenReturn(List.of(allDay, timed));
        when(writer.write(eq(1L), any(), anyList(), any(), any())).thenReturn(new GoogleCalendarImportWriter.Counts(2, 0));

        GoogleCalendarImportResult result = service.importEvents(1L, "token", "calendar@example.com", from, to);

        ArgumentCaptor<List<GoogleCalendarImportService.Candidate>> captured = ArgumentCaptor.forClass((Class) List.class);
        verify(writer).write(eq(1L), eq(GoogleCalendarImportService.hash("calendar@example.com")), captured.capture(), any(), any());
        var schedules = captured.getValue().stream().map(GoogleCalendarImportService.Candidate::schedule).toList();
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
        assertThat(schedules).allSatisfy(schedule -> {
            assertThat(schedule.getType()).isEqualTo(ScheduleType.PERSONAL);
            assertThat(schedule.getRepeatRule()).isNull();
            assertThat(schedule.getMemo()).isEqualTo("원본 메모");
        });
        assertThat(schedules.getFirst().isAllDay()).isTrue();
        assertThat(schedules.getFirst().getStartAt()).isEqualTo("2026-10-10T00:00:00");
        assertThat(schedules.getFirst().getEndAt()).isEqualTo("2026-10-12T00:00:00");
        assertThat(schedules.get(1).getStartAt()).isEqualTo("2026-10-13T12:00:00");
        assertThat(schedules.get(1).getEndAt()).isEqualTo("2026-10-13T13:00:00");
    }

    @Test
    void invalidEventsAreCountedWithoutInventingOrTruncatingContent() {
        EventTime start = new EventTime("2026-10-10", null);
        EventTime end = new EventTime("2026-10-11", null);
        var events = List.of(event("blank", "  ", start, end), event("long", "가".repeat(101), start, end),
                event("bad-time", "시간", new EventTime(null, "2026-10-10T12:00:00"), new EventTime(null, "2026-10-10T13:00:00")),
                event("outside", "이전", new EventTime("2026-09-29", null), new EventTime("2026-10-01", null)),
                new GoogleCalendarEvent("cancelled", "cancelled", "삭제", null, start, end),
                new GoogleCalendarEvent("memo", "confirmed", "메모", "가".repeat(22000), start, end),
                event("", "ID 없음", start, end));
        when(provider.fetchEvents("token", "calendar", from, to)).thenReturn(events);
        when(writer.write(eq(1L), any(), anyList(), any(), any())).thenReturn(new GoogleCalendarImportWriter.Counts(0, 0));

        var result = service.importEvents(1L, "token", "calendar", from, to);

        assertThat(result.skippedCount()).isEqualTo(7);
        assertThat(result.skippedReasons()).containsEntry(SkipReason.INVALID_TITLE, 2)
                .containsEntry(SkipReason.INVALID_TIME, 1).containsEntry(SkipReason.OUTSIDE_RANGE, 1)
                .containsEntry(SkipReason.CANCELLED, 1).containsEntry(SkipReason.MEMO_TOO_LONG, 1)
                .containsEntry(SkipReason.MISSING_EVENT_ID, 1);
        verify(writer).write(eq(1L), any(), eq(List.of()), any(), any());
    }

    @Test
    void rangeCapAndCanonicalIdAreCheckedBeforeAnyNetworkOrWrite() {
        assertInvalid("calendar", from, from.plusDays(366));
        assertInvalid("calendar", to, from);
        assertInvalid("primary", from, to);
        verifyNoInteractions(provider, writer);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exactlyOneThousandEventsCanBeProcessed() {
        when(provider.fetchEvents("token", "calendar", from, to)).thenReturn(Collections.nCopies(1000,
                event("id", "제목", new EventTime("2026-10-10", null), new EventTime("2026-10-11", null))));
        when(writer.write(eq(1L), any(), anyList(), any(), any())).thenReturn(new GoogleCalendarImportWriter.Counts(1, 999));
        var result = service.importEvents(1L, "token", "calendar", from, to);
        ArgumentCaptor<List<GoogleCalendarImportService.Candidate>> captured = ArgumentCaptor.forClass((Class) List.class);
        verify(writer).write(eq(1L), any(), captured.capture(), any(), any());
        assertThat(captured.getValue()).hasSize(1000);
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(999);
    }

    @Test
    void providerOverLimitIsAtomicAndNeverStartsWriter() {
        when(provider.fetchEvents("token", "calendar", from, to)).thenReturn(Collections.nCopies(1001,
                event("id", "제목", new EventTime("2026-10-10", null), new EventTime("2026-10-11", null))));
        assertThatThrownBy(() -> service.importEvents(1L, "token", "calendar", from, to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GoogleImportErrorCode.GOOGLE_IMPORT_LIMIT_EXCEEDED));
        verifyNoInteractions(writer);
    }

    @Test
    void oauthLockAndConsumeCallbacksArePassedIntoAtomicWriter() {
        Runnable lock = () -> {};
        Runnable consume = () -> {};
        when(provider.fetchEvents("token", "calendar", from, to)).thenReturn(List.of());
        when(writer.write(eq(1L), any(), anyList(), eq(lock), eq(consume)))
                .thenReturn(new GoogleCalendarImportWriter.Counts(0, 0));
        service.importEvents(1L, "token", "calendar", from, to, lock, consume);
        verify(writer).write(1L, GoogleCalendarImportService.hash("calendar"), List.of(), lock, consume);
    }

    private void assertInvalid(String calendar, LocalDate start, LocalDate end) {
        assertThatThrownBy(() -> service.importEvents(1L, "token", calendar, start, end))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GoogleImportErrorCode.GOOGLE_IMPORT_INVALID_INPUT));
    }

    private static GoogleCalendarEvent event(String id, String summary, EventTime start, EventTime end) {
        return new GoogleCalendarEvent(id, "confirmed", summary, "원본 메모", start, end);
    }
}
