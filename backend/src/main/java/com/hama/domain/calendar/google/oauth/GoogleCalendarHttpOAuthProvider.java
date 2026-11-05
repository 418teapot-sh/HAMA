package com.hama.domain.calendar.google.oauth;

import static com.hama.domain.calendar.google.oauth.GoogleCalendarOAuthErrorCode.*;

import com.hama.global.exception.BusinessException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GoogleCalendarHttpOAuthProvider implements GoogleCalendarOAuthProvider {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final GoogleCalendarOAuthSettings settings;
    private final JsonMapper json;
    private final Duration requestTimeout;
    private final WebClient http = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES)).build();

    @Autowired
    public GoogleCalendarHttpOAuthProvider(GoogleCalendarOAuthSettings settings, JsonMapper json) {
        this(settings, json, Duration.ofSeconds(20));
    }

    GoogleCalendarHttpOAuthProvider(GoogleCalendarOAuthSettings settings, JsonMapper json, Duration requestTimeout) {
        this.settings = settings;
        this.json = json;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Token exchange(String code, String verifier) {
        String body = "grant_type=authorization_code&code=" + encode(code)
                + "&client_id=" + encode(settings.clientId()) + "&client_secret=" + encode(settings.clientSecret())
                + "&redirect_uri=" + encode(settings.redirectUri()) + "&code_verifier=" + encode(verifier);
        JsonNode response = request(URI.create("https://oauth2.googleapis.com/token"), body, null, true);
        String type = response.path("token_type").asString("");
        String scope = response.path("scope").asString("");
        Set<String> granted = new HashSet<>(Arrays.asList(scope.split(" +")));
        if (!"Bearer".equalsIgnoreCase(type) || (!scope.isBlank()
                && !granted.containsAll(Arrays.asList(GoogleCalendarOAuthService.SCOPES.split(" "))))) {
            throw new BusinessException(GOOGLE_IMPORT_AUTH_INVALID);
        }
        // Only the short-lived access token is returned; refresh_token and id_token are never retained.
        return new Token(response.path("access_token").asString(""), response.path("expires_in").asLong(0));
    }

    @Override
    public List<GoogleCalendarAccount> calendars(String accessToken) {
        List<GoogleCalendarAccount> accounts = new ArrayList<>();
        Set<String> seenPages = new HashSet<>();
        String page = "";
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        do {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) throw upstream("Google calendar list timed out");
            if (!seenPages.add(page) || seenPages.size() > 40) throw upstream("Google calendar list exceeds limit");
            String url = "https://www.googleapis.com/calendar/v3/users/me/calendarList"
                    + "?maxResults=250&minAccessRole=reader&showDeleted=false&showHidden=false"
                    + "&fields=nextPageToken,items(id,summary,timeZone,primary,accessRole,deleted)"
                    + (page.isEmpty() ? "" : "&pageToken=" + encode(page));
            Duration remaining = Duration.ofNanos(remainingNanos);
            JsonNode response = request(URI.create(url), null, accessToken, false,
                    remaining.compareTo(requestTimeout) < 0 ? remaining : requestTimeout);
            if (System.nanoTime() >= deadline) throw upstream("Google calendar list timed out");
            JsonNode items = response.path("items");
            if (!items.isMissingNode() && !items.isArray()) throw upstream("Invalid Google calendar list");
            for (JsonNode item : items) {
                String id = item.path("id").asString("");
                if (id.isBlank() || item.path("deleted").asBoolean(false)) continue;
                accounts.add(new GoogleCalendarAccount(id, item.path("summary").asString(""),
                        item.path("timeZone").asString("Asia/Seoul"), item.path("primary").asBoolean(false),
                        item.path("accessRole").asString("")));
            }
            page = response.path("nextPageToken").asString("");
            if (page.length() > 4096) throw upstream("Invalid Google calendar page token");
        } while (!page.isEmpty());
        return List.copyOf(accounts);
    }

    JsonNode request(URI uri, String form, String accessToken, boolean exchange) {
        return request(uri, form, accessToken, exchange, requestTimeout);
    }

    private JsonNode request(URI uri, String form, String accessToken, boolean exchange, Duration timeout) {
        try {
            WebClient.RequestHeadersSpec<?> request = exchange
                    ? http.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).bodyValue(form)
                    : http.get().uri(uri).headers(headers -> headers.setBearerAuth(accessToken));
            byte[] body = request.retrieve().onStatus(status -> status.isError(), response -> {
                int code = response.statusCode().value();
                if (code == 401) return Mono.error(new BusinessException(GOOGLE_IMPORT_SESSION_EXPIRED));
                if (exchange && code == 400) return Mono.error(new BusinessException(GOOGLE_IMPORT_AUTH_INVALID));
                if (code == 403) return Mono.error(new BusinessException(GOOGLE_IMPORT_FORBIDDEN));
                return Mono.error(upstream("Google HTTP status " + code));
            }).bodyToMono(byte[].class).timeout(timeout).block();
            JsonNode parsed = body == null ? null : json.readTree(body);
            if (parsed == null || !parsed.isObject()) throw upstream("Invalid Google response object");
            return parsed;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            // Never attach raw provider exceptions: response bodies can contain credentials.
            throw upstream("Google request failed: " + e.getClass().getSimpleName());
        }
    }

    private static BusinessException upstream(String safeReason) {
        return new BusinessException(GOOGLE_IMPORT_UPSTREAM_ERROR, new IllegalStateException(safeReason));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
