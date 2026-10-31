package com.hama.domain.calendar.google.importer;

import com.hama.domain.calendar.google.importer.GoogleCalendarImportService.Candidate;
import com.hama.domain.schedule.entity.Schedule;
import com.hama.domain.schedule.repository.ScheduleRepository;
import com.hama.domain.user.exception.UserErrorCode;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarImportWriter {
    private final UserRepository users;
    private final ScheduleRepository schedules;
    private final JdbcTemplate jdbc;

    public GoogleCalendarImportWriter(UserRepository users, ScheduleRepository schedules, JdbcTemplate jdbc) {
        this.users = users;
        this.schedules = schedules;
        this.jdbc = jdbc;
    }

    /** 사용자별 병렬 가져오기와 회원 탈퇴를 직렬화하고 실패 시 세션 소비까지 모두 롤백합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Counts write(Long userId, String calendarHash, List<Candidate> candidates,
            Runnable lockSession, Runnable consumeSession) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        lockSession.run();
        int imported = 0;
        int duplicates = 0;
        for (Candidate candidate : candidates) {
            List<String> existing = jdbc.queryForList("""
                    SELECT calendar_uid FROM google_calendar_import_source
                    WHERE user_id = ? AND calendar_hash = ? AND event_hash = ?
                    """, String.class, userId, calendarHash, candidate.eventHash());
            if (!existing.isEmpty() && active(userId, existing.getFirst())) {
                duplicates++;
                continue;
            }
            Schedule saved = schedules.saveAndFlush(candidate.schedule());
            if (existing.isEmpty()) {
                jdbc.update("""
                        INSERT INTO google_calendar_import_source
                            (user_id, calendar_hash, event_hash, calendar_uid, created_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                        """, userId, calendarHash, candidate.eventHash(), saved.getCalendarUid());
            } else {
                jdbc.update("""
                        UPDATE google_calendar_import_source SET calendar_uid = ?
                        WHERE user_id = ? AND calendar_hash = ? AND event_hash = ?
                        """, saved.getCalendarUid(), userId, calendarHash, candidate.eventHash());
            }
            imported++;
        }
        consumeSession.run();
        return new Counts(imported, duplicates);
    }

    private boolean active(Long userId, String calendarUid) {
        // 한 SQL 스냅샷에서 양쪽을 읽어 PERSONAL↔TASK/OTHER 전환 중에도 동일 항목을 인식합니다.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT calendar_uid FROM schedule
                    WHERE user_id = ? AND calendar_uid = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT calendar_uid FROM todo
                    WHERE user_id = ? AND calendar_uid = ? AND deleted_at IS NULL
                )
                """, Boolean.class, userId, calendarUid, userId, calendarUid));
    }

    public record Counts(int importedCount, int duplicateCount) {}
}
