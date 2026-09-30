package com.hama.global.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 라이너 Chat Completions(OpenAI 호환) 요청·응답 형식. LinerAiClient 밖에서는 쓰지 않습니다.
 */
final class LinerChatCompletion {

    private LinerChatCompletion() {
    }

    /** 라이너는 모르는 필드를 400(unknown_parameter)으로 거절하므로 null 필드는 아예 보내지 않습니다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(
            String model,
            List<Message> messages,
            @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
            @JsonProperty("reasoning_effort") String reasoningEffort,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }

    record ResponseFormat(String type) {

        static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens,
            @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails
    ) {

        int cachedTokens() {
            return promptTokensDetails == null ? 0 : promptTokensDetails.cachedTokens();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptTokensDetails(@JsonProperty("cached_tokens") int cachedTokens) {
    }
}
