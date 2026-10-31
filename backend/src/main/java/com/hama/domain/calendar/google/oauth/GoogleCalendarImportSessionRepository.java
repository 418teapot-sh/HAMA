package com.hama.domain.calendar.google.oauth;

import com.hama.domain.user.exception.UserErrorCode;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GoogleCalendarImportSessionRepository {
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public GoogleCalendarImportSessionRepository(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    void insert(Session session, LocalDateTime createdAt) {
        users.findByIdForShare(session.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        jdbc.update("""
                INSERT INTO google_calendar_import_session
                (session_id,user_id,state_hash,verifier_cipher,access_token_cipher,expires_at,created_at)
                VALUES (?,?,?,?,NULL,?,?)
                """, session.id(), session.userId(), session.stateHash(), session.verifierCipher(),
                session.expiresAt(), createdAt);
    }

    Session find(String id, boolean lock) {
        List<Session> rows = jdbc.query("""
                SELECT session_id,user_id,state_hash,verifier_cipher,access_token_cipher,expires_at
                FROM google_calendar_import_session WHERE session_id = ?
                """ + (lock ? " FOR UPDATE" : ""), (rs, row) -> new Session(rs.getString("session_id"),
                rs.getLong("user_id"), rs.getString("state_hash"), rs.getString("verifier_cipher"),
                rs.getString("access_token_cipher"), rs.getObject("expires_at", LocalDateTime.class)), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    boolean claim(String id, long userId, String stateHash, LocalDateTime now) {
        return jdbc.update("""
                UPDATE google_calendar_import_session SET state_hash = 'CLAIMED', verifier_cipher = ''
                WHERE session_id = ? AND user_id = ? AND state_hash = ?
                  AND access_token_cipher IS NULL AND expires_at > ?
                """, id, userId, stateHash, now) == 1;
    }

    boolean authorize(String id, long userId, String token, LocalDateTime expiresAt, LocalDateTime now) {
        return jdbc.update("""
                UPDATE google_calendar_import_session SET access_token_cipher = ?, expires_at = ?
                WHERE session_id = ? AND user_id = ? AND state_hash = 'CLAIMED'
                  AND access_token_cipher IS NULL AND expires_at > ?
                """, token, expiresAt, id, userId, now) == 1;
    }

    void delete(String id, long userId) {
        jdbc.update("DELETE FROM google_calendar_import_session WHERE session_id = ? AND user_id = ?", id, userId);
    }

    void deleteExpired(LocalDateTime now) {
        jdbc.update("DELETE FROM google_calendar_import_session WHERE expires_at <= ?", now);
    }

    record Session(String id, long userId, String stateHash, String verifierCipher,
                   String accessTokenCipher, LocalDateTime expiresAt) {
        @Override public String toString() { return "GoogleCalendarImportSession[REDACTED]"; }
    }
}
