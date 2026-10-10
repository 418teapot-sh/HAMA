package com.hama.global.ai;

import java.util.List;

/**
 * AI 호출 한 번의 입력.
 *
 * @param purpose         로그·비용 추적용 태그 (예: "goal-session", "reality-check", "weekly-review")
 * @param systemPrompt    역할·출력 형식 지시. null 이면 생략합니다
 * @param messages        대화 이력(오래된 것부터). 최근 {@code liner.max-history-messages} 개만 전송되고, 잘릴 때도 첫 메시지는 유지합니다
 * @param maxTokens       이 호출의 생성 토큰 상한. null 이면 {@code liner.max-completion-tokens}, 그보다 크면 그 값으로 잘립니다
 * @param reasoningEffort null 이면 {@code liner.reasoning-effort}
 */
public record AiRequest(
        String purpose,
        String systemPrompt,
        List<AiMessage> messages,
        Integer maxTokens,
        String reasoningEffort
) {

    public AiRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static AiRequest of(String purpose, String systemPrompt, List<AiMessage> messages) {
        return new AiRequest(purpose, systemPrompt, messages, null, null);
    }

    public static AiRequest of(String purpose, String systemPrompt, String userMessage) {
        return of(purpose, systemPrompt, List.of(AiMessage.user(userMessage)));
    }

    public AiRequest withMaxTokens(int maxTokens) {
        return new AiRequest(purpose, systemPrompt, messages, maxTokens, reasoningEffort);
    }

    public AiRequest withReasoningEffort(String reasoningEffort) {
        return new AiRequest(purpose, systemPrompt, messages, maxTokens, reasoningEffort);
    }
}
