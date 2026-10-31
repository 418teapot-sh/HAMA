package com.hama.domain.calendar.google.oauth;

import static com.hama.domain.calendar.google.oauth.GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_UPSTREAM_ERROR;

import com.hama.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarTokenCipher {
    private final GoogleCalendarOAuthSettings settings;
    private final SecureRandom random = new SecureRandom();

    public GoogleCalendarTokenCipher(GoogleCalendarOAuthSettings settings) {
        this.settings = settings;
    }

    String encrypt(String value, String context) {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, context);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(GOOGLE_IMPORT_UPSTREAM_ERROR, e);
        }
    }

    String decrypt(String value, String context) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length < 28) throw new GeneralSecurityException("Invalid encrypted credential");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            return new String(cipher(Cipher.DECRYPT_MODE, iv, context)
                    .doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException(GOOGLE_IMPORT_UPSTREAM_ERROR, e);
        }
    }

    private Cipher cipher(int mode, byte[] iv, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(settings.encryptionKey(), "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
