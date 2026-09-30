package com.hama.global.ai;

import com.hama.global.ai.LinerChatCompletion.Choice;
import com.hama.global.ai.LinerChatCompletion.Message;
import com.hama.global.ai.LinerChatCompletion.Request;
import com.hama.global.ai.LinerChatCompletion.Response;
import com.hama.global.ai.LinerChatCompletion.ResponseFormat;
import com.hama.global.ai.LinerChatCompletion.Usage;
import com.hama.global.exception.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 라이너 Chat Completions 로 {@link AiClient} 를 구현합니다.
 *
 * <p>토큰 절감(이력 자르기, 생성 토큰 상한, 추론 강도)과 사용량 로그는 전부 여기서만 처리합니다.
 * 도메인 코드에서 따로 자르면 규칙이 두 군데로 갈라져 어느 쪽이 적용됐는지 알 수 없게 됩니다.
 */
@Slf4j
@Component
public class LinerAiClient implements AiClient {

    private static final String JSON_INSTRUCTION = "반드시 JSON 객체 하나로만 응답하세요.";
    private static final int LOG_BODY_LIMIT = 500;

    private final WebClient webClient;
    private final LinerProperties properties;
    private final JsonMapper jsonMapper;

    public LinerAiClient(@Qualifier(LinerConfig.LINER_WEB_CLIENT) WebClient webClient,
                         LinerProperties properties,
                         JsonMapper jsonMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        if (!properties.enabled()) {
            log.warn("LINER_API_KEY 가 비어 있습니다. AI 호출은 AI_NOT_CONFIGURED(503)로 실패합니다.");
        }
    }

    @Override
    public String chat(AiRequest request) {
        return call(request, false);
    }

    @Override
    public <T> T chatForJson(AiRequest request, Class<T> type) {
        String content = call(request, true);
        try {
            return jsonMapper.readValue(content, type);
        } catch (JacksonException e) {
            throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR, new IllegalStateException(
                    "[%s] AI 응답을 %s 로 파싱하지 못했습니다: %s"
                            .formatted(request.purpose(), type.getSimpleName(), abbreviate(content)), e));
        }
    }

    private String call(AiRequest request, boolean json) {
        if (!properties.enabled()) {
            throw new BusinessException(AiErrorCode.AI_NOT_CONFIGURED);
        }

        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .bodyValue(jsonMapper.writeValueAsString(toRequestBody(request, json)))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, LinerAiClient::toApiException)
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .block();
        } catch (LinerApiException e) {
            throw toBusinessException(request, e);
        } catch (RuntimeException e) {
            // 타임아웃·연결 실패. 라이너 문서상 재시도 가능한 상황이라 502 로 보냅니다.
            throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR,
                    new IllegalStateException("[%s] 라이너 호출 실패".formatted(request.purpose()), e));
        }

        return extractContent(request, responseBody);
    }

    Request toRequestBody(AiRequest request, boolean json) {
        List<Message> messages = new ArrayList<>();

        String systemPrompt = request.systemPrompt();
        // json_object 모드는 메시지에 "JSON" 이라는 단어가 없으면 라이너가 400 으로 거절합니다.
        if (json && (systemPrompt == null || !systemPrompt.contains("JSON"))) {
            systemPrompt = systemPrompt == null ? JSON_INSTRUCTION : systemPrompt + "\n" + JSON_INSTRUCTION;
        }
        if (systemPrompt != null) {
            messages.add(new Message("system", systemPrompt));
        }

        List<AiMessage> history = request.messages();
        int from = Math.max(0, history.size() - properties.maxHistoryMessages());
        for (AiMessage message : history.subList(from, history.size())) {
            messages.add(new Message(message.role().name().toLowerCase(), message.content()));
        }

        int maxTokens = request.maxTokens() == null
                ? properties.maxCompletionTokens()
                : Math.min(request.maxTokens(), properties.maxCompletionTokens());
        String reasoningEffort = request.reasoningEffort() == null
                ? properties.reasoningEffort()
                : request.reasoningEffort();

        return new Request(properties.model(), messages, maxTokens, reasoningEffort,
                json ? ResponseFormat.JSON_OBJECT : null);
    }

    private String extractContent(AiRequest request, String responseBody) {
        Response response;
        try {
            response = jsonMapper.readValue(responseBody, Response.class);
        } catch (JacksonException e) {
            throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR, new IllegalStateException(
                    "[%s] 라이너 응답 형식이 예상과 다릅니다: %s".formatted(request.purpose(), abbreviate(responseBody)), e));
        }

        logUsage(request, response.usage());

        if (response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null) {
            throw new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR, new IllegalStateException(
                    "[%s] 라이너 응답에 content 가 없습니다: %s".formatted(request.purpose(), abbreviate(responseBody))));
        }

        Choice choice = response.choices().getFirst();
        if ("length".equals(choice.finishReason())) {
            log.warn("[AI] purpose={} 응답이 max_completion_tokens 에 걸려 잘렸습니다. 프롬프트나 상한을 조정하세요.",
                    request.purpose());
        }
        return choice.message().content();
    }

    private void logUsage(AiRequest request, Usage usage) {
        if (usage == null) {
            return;
        }
        log.info("[AI] purpose={} promptTokens={} cachedTokens={} completionTokens={} totalTokens={}",
                request.purpose(), usage.promptTokens(), usage.cachedTokens(),
                usage.completionTokens(), usage.totalTokens());
    }

    private static Mono<LinerApiException> toApiException(ClientResponse response) {
        String requestId = response.headers().asHttpHeaders().getFirst("x-request-id");
        String retryAfter = response.headers().asHttpHeaders().getFirst("Retry-After");
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new LinerApiException(response.statusCode(), requestId, retryAfter, abbreviate(body)));
    }

    private BusinessException toBusinessException(AiRequest request, LinerApiException e) {
        if (e.status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            log.warn("[AI] purpose={} 라이너 rate limit (Retry-After={}, requestId={})",
                    request.purpose(), e.retryAfter, e.requestId);
            return new BusinessException(AiErrorCode.AI_RATE_LIMIT);
        }
        // 400(프롬프트·파라미터 문제), 401(키), 402(크레딧 부족), 5xx 모두 사용자가 고칠 수 없는 문제라 502 입니다.
        return new BusinessException(AiErrorCode.AI_UPSTREAM_ERROR, e);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= LOG_BODY_LIMIT ? text : text.substring(0, LOG_BODY_LIMIT) + "...";
    }

    /** 라이너가 에러 상태코드로 응답했을 때. 로그에서 라이너 쪽 요청을 찾을 수 있게 x-request-id 를 싣습니다. */
    static final class LinerApiException extends RuntimeException {

        private final HttpStatusCode status;
        private final String requestId;
        private final String retryAfter;

        LinerApiException(HttpStatusCode status, String requestId, String retryAfter, String body) {
            super("라이너 %d 응답 (requestId=%s): %s".formatted(status.value(), requestId, body));
            this.status = status;
            this.requestId = requestId;
            this.retryAfter = retryAfter;
        }
    }
}
