package com.hama.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hama.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 라이너에 실제로 요청하지 않고 WebClient 의 ExchangeFunction 을 바꿔 끼워서 확인합니다.
 */
class LinerAiClientTest {

    private static final LinerProperties PROPERTIES = new LinerProperties(
            "test-key", "http://liner.test", "liner-mark-1.1", 1, 1000, 2, "low");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

    @Test
    void 텍스트_응답의_content_를_돌려주고_Bearer_키를_붙인다() {
        AiClient client = clientReturning(HttpStatus.OK, completion("안녕하세요"));

        String answer = client.chat(AiRequest.of("test", "너는 코치야", "안녕"));

        assertThat(answer).isEqualTo("안녕하세요");
        assertThat(sentRequest.get().url().toString()).isEqualTo("http://liner.test/chat/completions");
        assertThat(sentRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-key");

        JsonNode body = sentBody();
        assertThat(body.get("model").asString()).isEqualTo("liner-mark-1.1");
        assertThat(body.get("messages").get(0).get("role").asString()).isEqualTo("system");
        assertThat(body.get("messages").get(1).get("content").asString()).isEqualTo("안녕");
        assertThat(body.get("reasoning_effort").asString()).isEqualTo("low");
        // null 필드를 보내면 라이너가 400(unknown_parameter)을 줄 수 있어 빠져 있어야 합니다.
        assertThat(body.has("response_format")).isFalse();
    }

    @Test
    void 대화_이력은_첫_메시지와_최근_메시지만_보내고_시스템_프롬프트는_항상_보낸다() {
        AiClient client = clientReturning(HttpStatus.OK, completion("ok"));
        List<AiMessage> history = new ArrayList<>(List.of(
                AiMessage.user("1"), AiMessage.assistant("2"), AiMessage.user("3"), AiMessage.assistant("4")));

        client.chat(AiRequest.of("test", "system", history));

        JsonNode messages = sentBody().get("messages");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).get("role").asString()).isEqualTo("system");
        assertThat(messages.get(1).get("content").asString()).isEqualTo("1");
        assertThat(messages.get(2).get("content").asString()).isEqualTo("4");
        assertThat(messages.get(2).get("role").asString()).isEqualTo("assistant");
    }

    @Test
    void 대화_이력이_상한_안이면_그대로_보내고_첫_메시지를_겹쳐_넣지_않는다() {
        AiClient client = clientReturning(HttpStatus.OK, completion("ok"));

        client.chat(AiRequest.of("test", "system", List.of(AiMessage.user("1"), AiMessage.assistant("2"))));

        JsonNode messages = sentBody().get("messages");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1).get("content").asString()).isEqualTo("1");
        assertThat(messages.get(2).get("content").asString()).isEqualTo("2");
    }

    @Test
    void 요청별_토큰_상한도_설정값을_넘을_수_없다() {
        AiClient client = clientReturning(HttpStatus.OK, completion("ok"));

        client.chat(AiRequest.of("test", null, "hi").withMaxTokens(99_999));

        assertThat(sentBody().get("max_completion_tokens").asInt()).isEqualTo(1000);
    }

    @Test
    void JSON_모드는_response_format_을_켜고_DTO_로_파싱한다() {
        AiClient client = clientReturning(HttpStatus.OK, completion("{\"verdict\":\"FEASIBLE\",\"requiredHours\":120}"));

        Verdict verdict = client.chatForJson(AiRequest.of("test", "현실성을 판단해", "토익 850"), Verdict.class);

        assertThat(verdict).isEqualTo(new Verdict("FEASIBLE", 120));
        JsonNode body = sentBody();
        assertThat(body.get("response_format").get("type").asString()).isEqualTo("json_object");
        // json_object 모드는 메시지에 "JSON" 단어가 없으면 라이너가 거절합니다.
        assertThat(body.get("messages").get(0).get("content").asString()).contains("JSON");
    }

    @Test
    void JSON_파싱에_실패하면_502() {
        AiClient client = clientReturning(HttpStatus.OK, completion("JSON 이 아닙니다"));

        assertThatThrownBy(() -> client.chatForJson(AiRequest.of("test", null, "hi"), Verdict.class))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_UPSTREAM_ERROR))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void 라이너가_429_면_AI_RATE_LIMIT() {
        AiClient client = clientReturning(HttpStatus.TOO_MANY_REQUESTS,
                "{\"error\":{\"message\":\"rate limit\",\"type\":\"rate_limit_error\"}}");

        assertThatThrownBy(() -> client.chat(AiRequest.of("test", null, "hi")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_RATE_LIMIT));
    }

    @Test
    void 라이너가_5xx_면_원인을_실어_AI_UPSTREAM_ERROR() {
        AiClient client = clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":{\"message\":\"boom\"}}");

        assertThatThrownBy(() -> client.chat(AiRequest.of("test", null, "hi")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_UPSTREAM_ERROR))
                .cause()
                .hasMessageContaining("500")
                .hasMessageContaining("req-123");
    }

    @Test
    void 응답이_타임아웃을_넘기면_AI_UPSTREAM_ERROR() {
        AiClient client = client(request -> {
            sentRequest.set(request);
            return Mono.never();
        }, PROPERTIES);

        assertThatThrownBy(() -> client.chat(AiRequest.of("test", null, "hi")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_UPSTREAM_ERROR));
    }

    @Test
    void choices_가_비어_있으면_AI_UPSTREAM_ERROR() {
        AiClient client = clientReturning(HttpStatus.OK, "{\"choices\":[]}");

        assertThatThrownBy(() -> client.chat(AiRequest.of("test", null, "hi")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_UPSTREAM_ERROR));
    }

    @Test
    void 토큰_상한에_걸려_잘린_응답은_AI_UPSTREAM_ERROR() {
        AiClient client = clientReturning(HttpStatus.OK, completion("문장이 중간에서 끊", "length"));

        assertThatThrownBy(() -> client.chat(AiRequest.of("test", null, "hi")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_UPSTREAM_ERROR))
                .cause()
                .hasMessageContaining("잘렸습니다");
        AiClient jsonClient = clientReturning(HttpStatus.OK, completion("{\"verdict\":\"FEAS", "length"));
        assertThatThrownBy(() -> jsonClient.chatForJson(AiRequest.of("test", null, "hi"), Verdict.class))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_UPSTREAM_ERROR));
    }

    @Test
    void content_가_빈_문자열이면_AI_UPSTREAM_ERROR() {
        AiClient client = clientReturning(HttpStatus.OK, completion(""));

        assertThatThrownBy(() -> client.chat(AiRequest.of("test", null, "hi")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_UPSTREAM_ERROR));
    }

    @Test
    void 사용량_필드가_null_이어도_content_를_돌려준다() {
        AiClient client = clientReturning(HttpStatus.OK, """
                {"choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":null,"completion_tokens":null,"total_tokens":null,
                          "prompt_tokens_details":{"cached_tokens":null}}}
                """);

        assertThat(client.chat(AiRequest.of("test", null, "hi"))).isEqualTo("ok");
    }

    @Test
    void API_키가_없으면_요청을_보내지_않고_AI_NOT_CONFIGURED() {
        LinerProperties noKey = new LinerProperties("", "http://liner.test", "liner-mark-1.1", 1, 1000, 2, "low");
        AiClient client = client(request -> {
            sentRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }, noKey);

        assertThatThrownBy(() -> client.chat(AiRequest.of("test", null, "hi")))
                .satisfies(e -> assertErrorCode(e, AiErrorCode.AI_NOT_CONFIGURED));
        assertThat(sentRequest.get()).isNull();
    }

    record Verdict(String verdict, int requiredHours) {
    }

    private AiClient clientReturning(HttpStatus status, String body) {
        return client(request -> {
            sentRequest.set(request);
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("x-request-id", "req-123")
                    .body(body)
                    .build());
        }, PROPERTIES);
    }

    private AiClient client(ExchangeFunction exchange, LinerProperties properties) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .exchangeFunction(exchange)
                .build();
        return new LinerAiClient(webClient, properties, jsonMapper);
    }

    private JsonNode sentBody() {
        ClientRequest request = sentRequest.get();
        MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(captured, ExchangeStrategies.withDefaults()).block();
        return jsonMapper.readTree(captured.getBodyAsString().block());
    }

    private static String completion(String content) {
        return completion(content, "stop");
    }

    private static String completion(String content, String finishReason) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"id":"chatcmpl-1","object":"chat.completion","model":"liner-mark-1.1",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"%s"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,"prompt_tokens_details":{"cached_tokens":0}}}
                """.formatted(escaped, finishReason);
    }

    private static void assertErrorCode(Throwable e, AiErrorCode expected) {
        assertThat(e).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }
}
