package com.hama.domain.calendar.google.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hama.global.exception.BusinessException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleCalendarOAuthServiceTest {
    private static final String ID = "17d02cb6-0810-4c99-b4c2-7793cc8a536e";
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"));
    private GoogleCalendarOAuthSettings settings;
    private GoogleCalendarTokenCipher cipher;
    private GoogleCalendarImportSessionRepository sessions;
    private GoogleCalendarOAuthProvider provider;
    private GoogleCalendarOAuthService service;

    @BeforeEach
    void setUp() {
        settings = new GoogleCalendarOAuthSettings("client", "secret", "https://app.example.test/google/callback",
                Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new GoogleCalendarTokenCipher(settings);
        sessions = mock(GoogleCalendarImportSessionRepository.class);
        provider = mock(GoogleCalendarOAuthProvider.class);
        service = new GoogleCalendarOAuthService(settings, cipher, sessions, provider, clock);
    }

    @Test
    void authorizationStoresOnlyHashedStateAndEncryptedVerifierAndUsesOnlinePkce() throws Exception {
        var response = service.begin(7);
        ArgumentCaptor<GoogleCalendarImportSessionRepository.Session> saved =
                ArgumentCaptor.forClass(GoogleCalendarImportSessionRepository.Session.class);
        verify(sessions).insert(saved.capture(), eq(now));
        var session = saved.getValue();
        String state = query(response.authorizationUrl(), "state");
        String verifier = cipher.decrypt(session.verifierCipher(), session.id() + ":7:verifier");
        assertThat(session.stateHash()).isEqualTo(hash(state)).isNotEqualTo(state);
        assertThat(session.verifierCipher()).doesNotContain(verifier);
        assertThat(query(response.authorizationUrl(), "code_challenge")).isEqualTo(Base64.getUrlEncoder()
                .withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.UTF_8))));
        assertThat(query(response.authorizationUrl(), "code_challenge_method")).isEqualTo("S256");
        assertThat(query(response.authorizationUrl(), "access_type")).isEqualTo("online");
        assertThat(query(response.authorizationUrl(), "redirect_uri")).isEqualTo("https://app.example.test/google/callback");
        assertThat(session.expiresAt()).isEqualTo(now.plusMinutes(10));
        assertThat(session.accessTokenCipher()).isNull();
        verifyNoInteractions(provider);
    }

    @Test
    void completeClaimsOnceBeforeNetworkAndStoresEncryptedShortLivedToken() throws Exception {
        var session = pending(7, now.plusMinutes(10));
        when(sessions.find(ID, false)).thenReturn(session);
        when(sessions.claim(ID, 7, session.stateHash(), now)).thenReturn(true);
        when(provider.exchange("code", "verifier")).thenReturn(new GoogleCalendarOAuthProvider.Token("secret-token", 3600));
        when(sessions.authorize(eq(ID), eq(7L), anyString(), eq(now.plusMinutes(10)), eq(now))).thenReturn(true);
        var result = service.complete(7, new GoogleCalendarOAuthService.CompleteRequest(ID, "state", "code"));
        var order = inOrder(sessions, provider);
        order.verify(sessions).find(ID, false);
        order.verify(sessions).claim(ID, 7, session.stateHash(), now);
        order.verify(provider).exchange("code", "verifier");
        ArgumentCaptor<String> encrypted = ArgumentCaptor.forClass(String.class);
        order.verify(sessions).authorize(eq(ID), eq(7L), encrypted.capture(), eq(now.plusMinutes(10)), eq(now));
        assertThat(encrypted.getValue()).doesNotContain("secret-token");
        assertThat(cipher.decrypt(encrypted.getValue(), ID + ":7:token")).isEqualTo("secret-token");
        assertThat(result.expiresAt()).isEqualTo(now.plusMinutes(10));
        assertThat(result.toString()).doesNotContain("secret-token");
    }

    @Test
    void wrongUserOrStateNeverClaimsOrCallsGoogle() throws Exception {
        when(sessions.find(ID, false)).thenReturn(pending(8, now.plusMinutes(10)));
        assertError(() -> service.complete(7, new GoogleCalendarOAuthService.CompleteRequest(ID, "state", "code")),
                GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_FORBIDDEN);
        when(sessions.find(ID, false)).thenReturn(pending(7, now.plusMinutes(10)));
        assertError(() -> service.complete(7, new GoogleCalendarOAuthService.CompleteRequest(ID, "other", "code")),
                GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_AUTH_INVALID);
        verify(sessions, never()).claim(anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(provider);
    }

    @Test
    void concurrentCallbackLosingClaimNeverExchangesCode() throws Exception {
        when(sessions.find(ID, false)).thenReturn(pending(7, now.plusMinutes(10)));
        assertError(() -> service.complete(7, new GoogleCalendarOAuthService.CompleteRequest(ID, "state", "code")),
                GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_AUTH_INVALID);
        verifyNoInteractions(provider);
    }

    @Test
    void expiredSessionDeletesCredentialAndNeverCallsGoogle() throws Exception {
        when(sessions.find(ID, false)).thenReturn(pending(7, now));
        assertError(() -> service.listCalendars(7, ID), GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_SESSION_EXPIRED);
        verify(sessions).delete(ID, 7);
        verifyNoInteractions(provider);
    }

    @Test
    void tokenExchangeFailureDeletesClaimedSession() throws Exception {
        var session = pending(7, now.plusMinutes(10));
        when(sessions.find(ID, false)).thenReturn(session);
        when(sessions.claim(ID, 7, session.stateHash(), now)).thenReturn(true);
        when(provider.exchange("code", "verifier"))
                .thenThrow(new BusinessException(GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_AUTH_INVALID));
        assertError(() -> service.complete(7, new GoogleCalendarOAuthService.CompleteRequest(ID, "state", "code")),
                GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_AUTH_INVALID);
        verify(sessions).delete(ID, 7);
    }

    @Test
    void onlyAuthorizedOwnedSessionExposesDecryptedTokenToServerCallback() {
        var session = new GoogleCalendarImportSessionRepository.Session(ID, 7, "CLAIMED", "",
                cipher.encrypt("temporary-token", ID + ":7:token"), now.plusMinutes(5));
        when(sessions.find(ID, false)).thenReturn(session);
        boolean tokenMatches = service.withSessionToken(7, ID, token -> token.equals("temporary-token"));
        assertThat(tokenMatches).isTrue();
        assertThat(session.toString()).doesNotContain("temporary-token");
        assertThat(new GoogleCalendarOAuthService.CompleteRequest(ID, "state", "code").toString())
                .doesNotContain("state", "code");
    }

    @Test
    void selectedCalendarMustBeInReadableGoogleList() {
        var account = new GoogleCalendarAccount("allowed", "My calendar", "Asia/Seoul", true, "owner");
        when(provider.calendars("token")).thenReturn(List.of(account));
        assertThat(service.requireCalendar("token", "allowed")).isEqualTo(account);
        assertError(() -> service.requireCalendar("token", "other"), GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_FORBIDDEN);
    }

    @Test
    void disabledFeatureDoesNotAccessDatabaseOrProvider() {
        var disabled = new GoogleCalendarOAuthSettings("", "", "", "");
        var disabledService = new GoogleCalendarOAuthService(disabled, new GoogleCalendarTokenCipher(disabled),
                sessions, provider, clock);
        assertError(() -> disabledService.begin(7), GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_DISABLED);
        verifyNoInteractions(sessions, provider);
    }

    private GoogleCalendarImportSessionRepository.Session pending(long userId, LocalDateTime expiresAt) throws Exception {
        return new GoogleCalendarImportSessionRepository.Session(ID, userId, hash("state"),
                cipher.encrypt("verifier", ID + ":" + userId + ":verifier"), null, expiresAt);
    }

    private static void assertError(Runnable action, GoogleCalendarOAuthErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static String query(String url, String key) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).filter(part -> part.startsWith(key + "="))
                .map(part -> URLDecoder.decode(part.substring(key.length() + 1), StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
