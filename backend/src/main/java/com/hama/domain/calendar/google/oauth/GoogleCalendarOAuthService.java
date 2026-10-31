package com.hama.domain.calendar.google.oauth;

import static com.hama.domain.calendar.google.oauth.GoogleCalendarOAuthErrorCode.*;

import com.hama.global.exception.BusinessException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarOAuthService {
    public static final String SCOPES = "https://www.googleapis.com/auth/calendar.calendarlist.readonly "
            + "https://www.googleapis.com/auth/calendar.events.readonly";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final GoogleCalendarOAuthSettings settings;
    private final GoogleCalendarTokenCipher cipher;
    private final GoogleCalendarImportSessionRepository sessions;
    private final GoogleCalendarOAuthProvider provider;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public GoogleCalendarOAuthService(GoogleCalendarOAuthSettings settings, GoogleCalendarTokenCipher cipher,
            GoogleCalendarImportSessionRepository sessions, GoogleCalendarOAuthProvider provider) {
        this(settings, cipher, sessions, provider, Clock.systemUTC());
    }

    GoogleCalendarOAuthService(GoogleCalendarOAuthSettings settings, GoogleCalendarTokenCipher cipher,
            GoogleCalendarImportSessionRepository sessions, GoogleCalendarOAuthProvider provider, Clock clock) {
        this.settings = settings;
        this.cipher = cipher;
        this.sessions = sessions;
        this.provider = provider;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NEVER)
    public Authorization begin(long userId) {
        requireEnabled();
        String id = UUID.randomUUID().toString();
        String state = randomValue();
        String verifier = randomValue();
        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plusMinutes(10);
        sessions.insert(new GoogleCalendarImportSessionRepository.Session(id, userId, hash(state),
                cipher.encrypt(verifier, context(id, userId, "verifier")), null, expiresAt), now);
        String url = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&access_type=online"
                + "&include_granted_scopes=false&prompt=consent&client_id=" + encode(settings.clientId())
                + "&redirect_uri=" + encode(settings.redirectUri()) + "&scope=" + encode(SCOPES)
                + "&state=" + encode(state) + "&code_challenge_method=S256&code_challenge="
                + encode(Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier)));
        return new Authorization(id, url, expiresAt);
    }

    @Transactional(propagation = Propagation.NEVER)
    public Authorized complete(long userId, CompleteRequest request) {
        requireEnabled();
        if (request == null || invalid(request.state(), 256) || invalid(request.code(), 4096)) {
            throw new BusinessException(GOOGLE_IMPORT_AUTH_INVALID);
        }
        var session = requireSession(userId, request.sessionId(), false, false);
        if (session.accessTokenCipher() != null || !constantEquals(session.stateHash(), hash(request.state()))) {
            throw new BusinessException(GOOGLE_IMPORT_AUTH_INVALID);
        }
        String verifier = cipher.decrypt(session.verifierCipher(), context(session.id(), userId, "verifier"));
        if (!sessions.claim(session.id(), userId, session.stateHash(), now())) {
            throw new BusinessException(GOOGLE_IMPORT_AUTH_INVALID);
        }
        try {
            var token = provider.exchange(request.code(), verifier);
            if (token == null || invalid(token.accessToken(), 16384) || token.expiresIn() <= 30) {
                throw new BusinessException(GOOGLE_IMPORT_UPSTREAM_ERROR,
                        new IllegalStateException("Google returned an invalid access token response"));
            }
            LocalDateTime now = now();
            LocalDateTime tokenExpiry = now.plusSeconds(Math.min(token.expiresIn() - 30, 600));
            LocalDateTime expiresAt = session.expiresAt().isBefore(tokenExpiry) ? session.expiresAt() : tokenExpiry;
            if (!expiresAt.isAfter(now) || !sessions.authorize(session.id(), userId,
                    cipher.encrypt(token.accessToken(), context(session.id(), userId, "token")), expiresAt, now)) {
                throw new BusinessException(GOOGLE_IMPORT_SESSION_EXPIRED);
            }
            return new Authorized(session.id(), expiresAt);
        } catch (RuntimeException e) {
            sessions.delete(session.id(), userId);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.NEVER)
    public List<GoogleCalendarAccount> listCalendars(long userId, String sessionId) {
        return withSessionToken(userId, sessionId, provider::calendars);
    }

    /** All external requests belong here, before the caller opens its write transaction. */
    @Transactional(propagation = Propagation.NEVER)
    public <T> T withSessionToken(long userId, String sessionId, Function<String, T> action) {
        requireEnabled();
        var session = requireSession(userId, sessionId, false, true);
        String token = cipher.decrypt(session.accessTokenCipher(), context(session.id(), userId, "token"));
        try {
            return action.apply(token);
        } catch (BusinessException e) {
            if (e.getErrorCode() == GOOGLE_IMPORT_SESSION_EXPIRED) sessions.delete(session.id(), userId);
            throw e;
        }
    }

    /** Validate the selected calendar against Google's readable list using the same scoped credential. */
    @Transactional(propagation = Propagation.NEVER)
    public GoogleCalendarAccount requireCalendar(String accessToken, String calendarId) {
        return provider.calendars(accessToken).stream().filter(calendar -> calendar.id().equals(calendarId))
                .findFirst().orElseThrow(() -> new BusinessException(GOOGLE_IMPORT_FORBIDDEN));
    }

    /** Caller locks the user first, then this session, then writes schedules in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForImport(long userId, String sessionId) {
        requireEnabled();
        requireSession(userId, sessionId, true, true);
    }

    /** Call after schedule writes, before commit; rollback restores both schedules and session token. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAfterSuccessfulImport(long userId, String sessionId) {
        sessions.delete(sessionId, userId);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanupExpiredSessions() {
        // Also clean tokens after configuration is disabled; no Google call is required.
        sessions.deleteExpired(now());
    }

    private GoogleCalendarImportSessionRepository.Session requireSession(
            long userId, String id, boolean lock, boolean authorized) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new BusinessException(GOOGLE_IMPORT_SESSION_NOT_FOUND);
        }
        var session = sessions.find(id, lock);
        if (session == null) throw new BusinessException(GOOGLE_IMPORT_SESSION_NOT_FOUND);
        if (session.userId() != userId) throw new BusinessException(GOOGLE_IMPORT_FORBIDDEN);
        if (!session.expiresAt().isAfter(now())) {
            sessions.delete(id, userId);
            throw new BusinessException(GOOGLE_IMPORT_SESSION_EXPIRED);
        }
        if (authorized && session.accessTokenCipher() == null) {
            throw new BusinessException(GOOGLE_IMPORT_NOT_AUTHORIZED);
        }
        return session;
    }

    private void requireEnabled() {
        if (!settings.enabled()) throw new BusinessException(GOOGLE_IMPORT_DISABLED,
                new IllegalStateException("Google Calendar import configuration is absent or invalid"));
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), KST); }
    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static boolean invalid(String value, int max) { return value == null || value.isBlank() || value.length() > max; }
    private static String context(String id, long userId, String purpose) { return id + ":" + userId + ":" + purpose; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String hash(String value) { return HexFormat.of().formatHex(digest(value)); }
    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private static boolean constantEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public record Authorization(String sessionId, String authorizationUrl, LocalDateTime expiresAt) {
        @Override public String toString() { return "Authorization[REDACTED]"; }
    }
    public record Authorized(String sessionId, LocalDateTime expiresAt) { }
    public record CompleteRequest(String sessionId, String state, String code) {
        @Override public String toString() { return "CompleteRequest[REDACTED]"; }
    }
}
