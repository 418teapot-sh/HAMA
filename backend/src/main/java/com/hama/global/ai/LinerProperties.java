package com.hama.global.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 liner.* 설정값.
 *
 * @param apiKey              비어 있으면 AI 호출이 {@code AI_NOT_CONFIGURED} 로 실패합니다(부팅은 됩니다)
 * @param baseUrl             OpenAI 호환 엔드포인트. {@code {baseUrl}/chat/completions} 로 호출합니다
 * @param model               예: liner-mark-1.1
 * @param timeoutSeconds      응답 대기 상한. 넘으면 {@code AI_UPSTREAM_ERROR}
 * @param maxCompletionTokens 호출 한 번에 생성할 수 있는 토큰 상한. 요청별 값도 이걸 넘을 수 없습니다
 * @param maxHistoryMessages  대화 이력 중 최근 몇 개만 보낼지. 잘릴 때도 첫 메시지는 이 개수 안에서 유지합니다. 시스템 프롬프트는 항상 포함되고 개수에서 빠집니다
 * @param reasoningEffort     none | low | medium | high | max. 추론 토큰도 과금되므로 기본은 낮게 둡니다
 */
@ConfigurationProperties(prefix = "liner")
public record LinerProperties(
        String apiKey,
        String baseUrl,
        String model,
        int timeoutSeconds,
        int maxCompletionTokens,
        int maxHistoryMessages,
        String reasoningEffort
) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
