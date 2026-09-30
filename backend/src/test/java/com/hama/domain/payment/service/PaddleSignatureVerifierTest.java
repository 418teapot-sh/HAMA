package com.hama.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PaddleSignatureVerifierTest {

    private static final String SECRET = "pdl_ntfset_test";
    private static final long NOW = 1_790_000_000L;
    private static final byte[] BODY = "{\"event_type\":\"transaction.completed\",\"name\":\"하마\"}"
            .getBytes(StandardCharsets.UTF_8);

    private final PaddleSignatureVerifier verifier = verifierWith(SECRET);

    private static PaddleSignatureVerifier verifierWith(String secret) {
        return new PaddleSignatureVerifier(new PaddleProperties(secret),
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    private static String sign(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ":").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 올바른_서명이면_통과한다() {
        assertThat(verifier.isValid("ts=" + NOW + ";h1=" + sign(SECRET, NOW, BODY), BODY)).isTrue();
    }

    @Test
    void 본문이_한_글자라도_바뀌면_거절한다() {
        String header = "ts=" + NOW + ";h1=" + sign(SECRET, NOW, BODY);
        byte[] tampered = "{\"event_type\":\"transaction.completed\",\"name\":\"하마!\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(header, tampered)).isFalse();
    }

    @Test
    void 다른_키로_만든_서명은_거절한다() {
        assertThat(verifier.isValid("ts=" + NOW + ";h1=" + sign("other-secret", NOW, BODY), BODY)).isFalse();
    }

    @Test
    void 허용_시간보다_오래된_서명은_거절한다() {
        long old = NOW - PaddleSignatureVerifier.TOLERANCE_SECONDS - 1;

        assertThat(verifier.isValid("ts=" + old + ";h1=" + sign(SECRET, old, BODY), BODY)).isFalse();
    }

    @Test
    void 시크릿_교체_중_h1_이_여러_개면_하나만_맞아도_통과한다() {
        String header = "ts=" + NOW + ";h1=" + sign("old-secret", NOW, BODY) + ";h1=" + sign(SECRET, NOW, BODY);

        assertThat(verifier.isValid(header, BODY)).isTrue();
    }

    @Test
    void 시크릿이_비어_있으면_어떤_서명이든_거절한다() {
        assertThat(verifierWith("").isValid("ts=" + NOW + ";h1=" + sign("anything", NOW, BODY), BODY)).isFalse();
        assertThat(verifierWith(null).isValid("ts=" + NOW + ";h1=" + sign("anything", NOW, BODY), BODY)).isFalse();
    }

    @Test
    void 헤더가_없거나_형식이_틀리면_거절한다() {
        assertThat(verifier.isValid(null, BODY)).isFalse();
        assertThat(verifier.isValid("garbage", BODY)).isFalse();
        assertThat(verifier.isValid("ts=" + NOW + ";h1=not-hex", BODY)).isFalse();
    }
}
