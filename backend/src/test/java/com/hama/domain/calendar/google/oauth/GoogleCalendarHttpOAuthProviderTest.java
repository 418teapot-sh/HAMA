package com.hama.domain.calendar.google.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.global.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GoogleCalendarHttpOAuthProviderTest {
    @Test
    void 코드_교환은_폼_본문을_전송하고_JSON을_읽는다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var seen = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/token", exchange -> {
            seen.set(exchange.getRequestHeaders().getFirst("Content-Type") + ":"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"access_token\":\"test-only\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new GoogleCalendarHttpOAuthProvider(new GoogleCalendarOAuthSettings("", "", "", ""),
                    JsonMapper.builder().build(), Duration.ofSeconds(3));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/token");
            assertThat(provider.request(uri, "grant_type=authorization_code&code=test", null, true)
                    .get("access_token").asString()).isEqualTo("test-only");
            assertThat(seen.get()).startsWith("application/x-www-form-urlencoded:")
                    .endsWith("grant_type=authorization_code&code=test");
        } finally { server.stop(0); }
    }

    @Test
    void headers만_도착하고_본문이_멈추면_timeout되고_민감값을_오류에_남기지_않는다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("{\"partial\":".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var settings = new GoogleCalendarOAuthSettings("", "", "", "");
            var provider = new GoogleCalendarHttpOAuthProvider(settings, JsonMapper.builder().build(), Duration.ofMillis(150));
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow");
            long start = System.nanoTime();
            assertThatThrownBy(() -> provider.request(uri, null, "private-token", false))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(GoogleCalendarOAuthErrorCode.GOOGLE_IMPORT_UPSTREAM_ERROR);
                        assertThat(exception.toString()).doesNotContain("private-token");
                        assertThat(exception.getCause().toString()).doesNotContain("private-token", "partial");
                    });
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
