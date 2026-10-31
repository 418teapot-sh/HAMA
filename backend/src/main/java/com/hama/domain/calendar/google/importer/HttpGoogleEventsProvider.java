package com.hama.domain.calendar.google.importer;

import com.hama.domain.shared.time.Be3Time;
import com.hama.global.exception.BusinessException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class HttpGoogleEventsProvider implements GoogleEventsProvider {
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");
    private final JsonMapper json;
    private final WebClient client;
    private final String endpoint;

    @Autowired
    public HttpGoogleEventsProvider(JsonMapper json,
            @Value("${google.calendar.api-base-url:https://www.googleapis.com/calendar/v3}") String endpoint) {
        this.json = json;
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.client = WebClient.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(4 * 1024 * 1024)).build();
    }

    @Override
    public List<GoogleCalendarEvent> fetchEvents(String accessToken, String calendarId, LocalDate from, LocalDate to) {
        var events = new ArrayList<GoogleCalendarEvent>();
        var tokens = new HashSet<String>();
        String pageToken = null;
        Instant deadline = Instant.now().plusSeconds(60);
        do {
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) throw upstream("Google events total request timeout");
            String query = "singleEvents=true&showDeleted=false&maxResults=1000&timeZone=Asia%2FSeoul"
                    + "&timeMin=" + encode(from.atStartOfDay(Be3Time.KST).format(RFC3339))
                    + "&timeMax=" + encode(to.plusDays(1).atStartOfDay(Be3Time.KST).format(RFC3339))
                    + "&fields=" + encode("nextPageToken,items(id,status,summary,description,start(date,dateTime),end(date,dateTime))")
                    + (pageToken == null ? "" : "&pageToken=" + encode(pageToken));
            URI uri = URI.create(endpoint + "/calendars/" + encode(calendarId) + "/events?" + query);
            JsonNode root;
            try {
                String body = client.get().uri(uri).headers(headers -> headers.setBearerAuth(accessToken))
                        .retrieve().onStatus(status -> status.isError(), response -> {
                            if (response.statusCode().value() == 401 || response.statusCode().value() == 403
                                    || response.statusCode().value() == 404) {
                                return Mono.error(new BusinessException(GoogleImportErrorCode.GOOGLE_IMPORT_ACCESS_DENIED));
                            }
                            return Mono.error(upstream("Google HTTP " + response.statusCode().value()));
                        }).bodyToMono(String.class)
                        .timeout(remaining.compareTo(Duration.ofSeconds(15)) < 0 ? remaining : Duration.ofSeconds(15)).block();
                root = json.readTree(body);
            } catch (BusinessException e) {
                throw e;
            } catch (RuntimeException e) {
                // 요청 헤더·응답 본문·토큰을 예외 및 로그에 포함하지 않습니다.
                throw upstream("Google events request failed: " + e.getClass().getSimpleName());
            }
            if (root == null || !root.isObject() || (root.has("items") && !root.path("items").isArray())) {
                throw upstream("Invalid Google events envelope");
            }
            for (JsonNode event : root.path("items")) {
                events.add(new GoogleCalendarEvent(string(event, "id"), string(event, "status"),
                        string(event, "summary"), string(event, "description"),
                        time(event.path("start")), time(event.path("end"))));
                if (events.size() > GoogleCalendarImportService.MAX_EVENTS) {
                    throw new BusinessException(GoogleImportErrorCode.GOOGLE_IMPORT_LIMIT_EXCEEDED);
                }
            }
            pageToken = string(root, "nextPageToken");
            if (pageToken != null && pageToken.isBlank()) pageToken = null;
            if (pageToken != null && (!tokens.add(pageToken) || tokens.size() > 1000)) {
                throw upstream("Invalid Google events pagination");
            }
        } while (pageToken != null);
        return List.copyOf(events);
    }

    private static GoogleCalendarEvent.EventTime time(JsonNode node) {
        return new GoogleCalendarEvent.EventTime(string(node, "date"), string(node, "dateTime"));
    }

    private static String string(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isString() ? value.stringValue() : null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static BusinessException upstream(String reason) {
        return new BusinessException(GoogleImportErrorCode.GOOGLE_IMPORT_UPSTREAM_ERROR, new IllegalStateException(reason));
    }
}
