package com.hama.domain.calendar.google.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.global.exception.BusinessException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class GoogleCalendarTokenCipherTest {
    private final GoogleCalendarOAuthSettings settings = new GoogleCalendarOAuthSettings("client", "secret",
            "https://app.example.test/oauth", Base64.getEncoder().encodeToString(new byte[32]));
    private final GoogleCalendarTokenCipher cipher = new GoogleCalendarTokenCipher(settings);

    @Test
    void randomIvChangesCiphertextAndContextBindsSessionUserAndPurpose() {
        String context = "session:7:token";
        String first = cipher.encrypt("credential", context);
        String second = cipher.encrypt("credential", context);
        assertThat(first).isNotEqualTo(second).doesNotContain("credential");
        assertThat(cipher.decrypt(first, context)).isEqualTo("credential");
        assertThatThrownBy(() -> cipher.decrypt(first, "session:8:token")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cipher.decrypt(first, "session:7:verifier")).isInstanceOf(BusinessException.class);
    }

    @Test
    void tamperingIsRejected() {
        byte[] payload = Base64.getDecoder().decode(cipher.encrypt("credential", "session:7:token"));
        payload[payload.length - 1] ^= 1;
        String changed = Base64.getEncoder().encodeToString(payload);
        assertThatThrownBy(() -> cipher.decrypt(changed, "session:7:token")).isInstanceOf(BusinessException.class);
    }

    @Test
    void badConfigDisablesOnlyGoogleImportAndLoopbackIsAvailableForDevelopment() {
        assertThat(new GoogleCalendarOAuthSettings("client", "secret", "https://app.example.test/oauth", "invalid").enabled()).isFalse();
        assertThat(new GoogleCalendarOAuthSettings("client", "secret", "http://remote.example.test/oauth",
                Base64.getEncoder().encodeToString(new byte[32])).enabled()).isFalse();
        assertThat(new GoogleCalendarOAuthSettings("client", "secret", "http://localhost:5173/google/callback",
                Base64.getEncoder().encodeToString(new byte[32])).enabled()).isTrue();
    }
}
