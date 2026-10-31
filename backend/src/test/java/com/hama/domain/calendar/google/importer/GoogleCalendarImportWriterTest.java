package com.hama.domain.calendar.google.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hama.domain.calendar.google.importer.GoogleCalendarImportService.Candidate;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.entity.ScheduleType;
import com.hama.domain.schedule.repository.ScheduleRepository;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class GoogleCalendarImportWriterTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GoogleCalendarImportWriter writer = new GoogleCalendarImportWriter(users, schedules, jdbc);
    private final Runnable lockSession = mock(Runnable.class);
    private final Runnable consumeSession = mock(Runnable.class);

    @Test
    void activeMappingPreservesOriginalInsteadOfOverwritingIncludingConvertedTodos() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq("calendar-hash"), eq("event-hash")))
                .thenReturn(List.of("original-uid"));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("original-uid"), eq(1L), eq("original-uid")))
                .thenReturn(true);

        var counts = writer.write(1L, "calendar-hash", List.of(candidate()), lockSession, consumeSession);

        assertThat(counts).isEqualTo(new GoogleCalendarImportWriter.Counts(0, 1));
        verifyNoInteractions(schedules);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Boolean.class), eq(1L), eq("original-uid"), eq(1L), eq("original-uid"));
        assertThat(sql.getValue()).contains("FROM schedule", "UNION ALL", "FROM todo", "deleted_at IS NULL");
        var order = inOrder(users, lockSession, jdbc, consumeSession);
        order.verify(users).findByIdForUpdate(1L);
        order.verify(lockSession).run();
        order.verify(jdbc).queryForList(anyString(), eq(String.class), eq(1L), eq("calendar-hash"), eq("event-hash"));
        order.verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("original-uid"), eq(1L), eq("original-uid"));
        order.verify(consumeSession).run();
    }

    @Test
    void deletedMappingCreatesNewScheduleAndReplacesMappingUid() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq("calendar-hash"), eq("event-hash")))
                .thenReturn(List.of("deleted-uid"));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(1L), eq("deleted-uid"), eq(1L), eq("deleted-uid")))
                .thenReturn(false);
        var candidate = candidate();
        when(schedules.saveAndFlush(candidate.schedule())).thenReturn(candidate.schedule());

        var counts = writer.write(1L, "calendar-hash", List.of(candidate), lockSession, consumeSession);

        assertThat(counts).isEqualTo(new GoogleCalendarImportWriter.Counts(1, 0));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(candidate.schedule().getCalendarUid()), eq(1L), eq("calendar-hash"), eq("event-hash"));
        assertThat(sql.getValue()).contains("UPDATE google_calendar_import_source SET calendar_uid");
        assertThat(candidate.schedule().getCalendarUid()).isNotEqualTo("deleted-uid");
    }

    @Test
    void firstImportWritesMappingAfterSavingSchedule() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq("calendar-hash"), eq("event-hash")))
                .thenReturn(List.of());
        var candidate = candidate();
        when(schedules.saveAndFlush(candidate.schedule())).thenReturn(candidate.schedule());

        var counts = writer.write(1L, "calendar-hash", List.of(candidate), lockSession, consumeSession);

        assertThat(counts).isEqualTo(new GoogleCalendarImportWriter.Counts(1, 0));
        var order = inOrder(schedules, jdbc, consumeSession);
        order.verify(schedules).saveAndFlush(candidate.schedule());
        order.verify(jdbc).update(anyString(), eq(1L), eq("calendar-hash"), eq("event-hash"), eq(candidate.schedule().getCalendarUid()));
        order.verify(consumeSession).run();
    }

    @Test
    void withdrawnUserCannotWriteOrConsumeSession() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> writer.write(1L, "calendar-hash", List.of(candidate()), lockSession, consumeSession))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(schedules, jdbc, lockSession, consumeSession);
    }

    @Test
    void invalidatedSessionDoesNotStartAnyScheduleWrite() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        org.mockito.Mockito.doThrow(new IllegalStateException("session expired")).when(lockSession).run();
        assertThatThrownBy(() -> writer.write(1L, "calendar-hash", List.of(candidate()), lockSession, consumeSession))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(schedules, jdbc, consumeSession);
    }

    private static Candidate candidate() {
        return new Candidate("event-hash", Schedule.create(1L, ScheduleType.PERSONAL, "원본",
                LocalDateTime.of(2026, 10, 10, 9, 0), LocalDateTime.of(2026, 10, 10, 10, 0), false, null, null));
    }
}
