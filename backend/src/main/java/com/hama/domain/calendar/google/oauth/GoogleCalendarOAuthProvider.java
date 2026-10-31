package com.hama.domain.calendar.google.oauth;

import java.util.List;

/** Provider boundary: tests must replace this instead of contacting Google. */
public interface GoogleCalendarOAuthProvider {
    Token exchange(String code, String verifier);
    List<GoogleCalendarAccount> calendars(String accessToken);

    record Token(String accessToken, long expiresIn) {
        @Override public String toString() { return "Token[REDACTED]"; }
    }
}
