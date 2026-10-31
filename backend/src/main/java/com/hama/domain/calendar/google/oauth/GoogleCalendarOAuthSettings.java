package com.hama.domain.calendar.google.oauth;

import java.net.URI;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarOAuthSettings {
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final byte[] encryptionKey;

    public GoogleCalendarOAuthSettings(
            @Value("${GOOGLE_CALENDAR_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CALENDAR_CLIENT_SECRET:}") String clientSecret,
            @Value("${GOOGLE_CALENDAR_REDIRECT_URI:}") String redirectUri,
            @Value("${GOOGLE_CALENDAR_ENCRYPTION_KEY:}") String encryptionKey) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        byte[] decoded = null;
        try {
            byte[] candidate = Base64.getDecoder().decode(encryptionKey);
            URI redirect = URI.create(redirectUri);
            boolean secure = "https".equals(redirect.getScheme())
                    || ("http".equals(redirect.getScheme())
                    && ("localhost".equals(redirect.getHost()) || "127.0.0.1".equals(redirect.getHost())));
            if (candidate.length == 32 && secure && redirect.getHost() != null
                    && redirect.getUserInfo() == null && redirect.getFragment() == null) {
                decoded = candidate;
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid/missing optional configuration disables this feature, not application startup.
        }
        this.encryptionKey = decoded;
    }

    public boolean enabled() {
        return !clientId.isBlank() && !clientSecret.isBlank() && encryptionKey != null;
    }

    String clientId() { return clientId; }
    String clientSecret() { return clientSecret; }
    String redirectUri() { return redirectUri; }
    byte[] encryptionKey() { return encryptionKey == null ? null : encryptionKey.clone(); }
}
