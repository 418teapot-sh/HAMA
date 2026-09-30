package com.hama.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Paddle 웹훅의 {@code Paddle-Signature} 헤더를 검증합니다.
 * https://developer.paddle.com/webhooks/signature-verification
 *
 * <pre>
 * Paddle-Signature: ts=1671552777;h1=eb4d0dc8...
 * h1 = hex( HMAC-SHA256( key = 웹훅 시크릿, message = ts + ":" + 원본 본문 ) )
 * </pre>
 *
 * 이 검증이 없으면 누구나 "결제 완료" 웹훅을 만들어 보내서 프리미엄을 켤 수 있습니다.
 */
@Slf4j
@Component
class PaddleSignatureVerifier {

    /**
     * Paddle SDK 기본값(5초). 오래된 웹훅을 그대로 다시 보내는 재전송 공격을 막습니다.
     * 서버 시계가 틀어져서 정상 웹훅까지 거절되면 이 값을 늘리세요.
     */
    static final long TOLERANCE_SECONDS = 5;

    private final String secret;
    private final Clock clock;

    @Autowired
    PaddleSignatureVerifier(PaddleProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PaddleSignatureVerifier(PaddleProperties properties, Clock clock) {
        this.secret = properties.webhookSecret();
        this.clock = clock;
    }

    /** 본문은 받은 바이트 그대로 넘겨야 합니다. 공백 하나만 달라져도 서명이 맞지 않습니다. */
    boolean isValid(String header, byte[] rawBody) {
        // 빈 키로 만든 서명은 누구나 계산할 수 있으므로, 키가 없으면 전부 거절합니다.
        if (secret == null || secret.isBlank()) {
            log.error("[Payment] paddle.webhook-secret 이 비어 있어 웹훅을 거절합니다. PADDLE_WEBHOOK_SECRET 을 설정하세요.");
            return false;
        }
        if (header == null) {
            return false;
        }

        String timestamp = null;
        List<String> signatures = new ArrayList<>();   // 시크릿 교체 중에는 h1 이 여러 개 옵니다.
        for (String part : header.split(";")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if (keyValue[0].equals("ts")) {
                timestamp = keyValue[1];
            } else if (keyValue[0].equals("h1")) {
                signatures.add(keyValue[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty() || !isFresh(timestamp)) {
            return false;
        }

        byte[] expected = hmacSha256((timestamp + ":").getBytes(StandardCharsets.UTF_8), rawBody);
        return signatures.stream().anyMatch(signature -> matches(expected, signature));
    }

    private boolean isFresh(String timestamp) {
        try {
            long age = clock.instant().getEpochSecond() - Long.parseLong(timestamp);
            return Math.abs(age) <= TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 비교 시간이 값에 따라 달라지지 않도록 {@link MessageDigest#isEqual} 로 비교합니다. */
    private static boolean matches(byte[] expected, String hexSignature) {
        try {
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(hexSignature));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] hmacSha256(byte[] prefix, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(prefix);
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 을 사용할 수 없습니다.", e);
        }
    }
}
