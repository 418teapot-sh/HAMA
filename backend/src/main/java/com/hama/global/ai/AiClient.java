package com.hama.global.ai;

/**
 * goals/ai(BE2)·reviews(BE3)가 공통으로 쓰는 AI 호출 창구입니다.
 *
 * <p>실패는 전부 {@code BusinessException(AiErrorCode)} 로 나갑니다. 호출하는 쪽은 잡지 말고 그대로 던지면
 * GlobalExceptionHandler 가 429 / 502 / 503 으로 응답합니다.
 */
public interface AiClient {

    /** 텍스트 응답을 그대로 돌려줍니다. */
    String chat(AiRequest request);

    /**
     * JSON 모드로 호출해서 응답을 {@code type} 으로 파싱합니다. 필드명은 시스템 프롬프트에 적어주세요.
     * 파싱에 실패하면 {@code AI_UPSTREAM_ERROR} 입니다.
     */
    <T> T chatForJson(AiRequest request, Class<T> type);
}
