package com.hama.domain.calendar.google.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.global.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpGoogleEventsProviderTest {
    private HttpServer server;
    private final LocalDate from = LocalDate.of(2026, 10, 1);
    private final LocalDate to = LocalDate.of(2026, 10, 31);

    @AfterEach
    void close() { if (server != null) server.stop(0); }

    @Test
    void followsOpaquePageTokensAndUsesExpandedInstancesAndExclusiveKstBounds() throws IOException {
        List<String> requests = new ArrayList<>();
        var provider = provider(exchange -> {
            requests.add(exchange.getRequestURI().toASCIIString());
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-only-token");
            return exchange.getRequestURI().getRawQuery().contains("pageToken=")
                    ? "{\"items\":[{\"id\":\"two\",\"start\":{\"date\":\"2026-10-10\"},\"end\":{\"date\":\"2026-10-11\"}}]}"
                    : "{\"items\":[{\"id\":\"one\"}],\"nextPageToken\":\"opaque+/ token\"}";
        }, 200);

        var events = provider.fetchEvents("test-only-token", "id+suffix@example.com", from, to);

        assertThat(events).extracting(GoogleCalendarEvent::id).containsExactly("one", "two");
        assertThat(events.get(1).start().date()).isEqualTo("2026-10-10");
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst()).contains("/calendars/id%2Bsuffix%40example.com/events?")
                .contains("singleEvents=true").contains("showDeleted=false")
                .contains("timeMin=2026-10-01T00%3A00%3A00%2B09%3A00")
                .contains("timeMax=2026-11-01T00%3A00%3A00%2B09%3A00");
        assertThat(requests.get(1)).contains("pageToken=opaque%2B%2F%20token");
    }

    @Test
    void repeatsInPaginationFailInsteadOfPartiallyImporting() throws IOException {
        var provider = provider(exchange -> "{\"items\":[],\"nextPageToken\":\"same\"}", 200);
        assertThatThrownBy(() -> provider.fetchEvents("test-only-token", "calendar", from, to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GoogleImportErrorCode.GOOGLE_IMPORT_UPSTREAM_ERROR));
    }

    @Test
    void inaccessibleCalendarsFailWithoutReturningProviderResponseOrToken() throws IOException {
        var provider = provider(exchange -> "{\"error\":\"private provider details\"}", 403);
        assertThatThrownBy(() -> provider.fetchEvents("test-only-token", "calendar", from, to))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GoogleImportErrorCode.GOOGLE_IMPORT_ACCESS_DENIED);
                    assertThat(e.toString()).doesNotContain("test-only-token", "private provider details");
                });
    }

    @Test
    void malformedResponseDoesNotLeakProviderBodyInExceptionCause() throws IOException {
        var provider = provider(exchange -> "private provider details", 200);
        assertThatThrownBy(() -> provider.fetchEvents("test-only-token", "calendar", from, to))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GoogleImportErrorCode.GOOGLE_IMPORT_UPSTREAM_ERROR);
                    assertThat(e.getCause().toString()).doesNotContain("private provider details", "test-only-token");
                });
    }

    @Test
    void moreThanThousandInstancesRejectsWholeResponse() throws IOException {
        String body = "{\"items\":[" + String.join(",", java.util.Collections.nCopies(1001, "{\"id\":\"one\"}")) + "]}";
        var provider = provider(exchange -> body, 200);
        assertThatThrownBy(() -> provider.fetchEvents("test-only-token", "calendar", from, to))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GoogleImportErrorCode.GOOGLE_IMPORT_LIMIT_EXCEEDED));
    }

    private HttpGoogleEventsProvider provider(java.util.function.Function<com.sun.net.httpserver.HttpExchange, String> response,
            int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = response.apply(exchange).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        return new HttpGoogleEventsProvider(JsonMapper.builder().build(), "http://127.0.0.1:" + server.getAddress().getPort());
    }
}
