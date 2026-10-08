package com.hama.global.ai;

/**
 * 대화 이력 한 건. 시스템 프롬프트는 여기가 아니라 {@link AiRequest#systemPrompt()} 에 넣습니다.
 */
public record AiMessage(Role role, String content) {

    public enum Role {
        USER, ASSISTANT
    }

    public static AiMessage user(String content) {
        return new AiMessage(Role.USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(Role.ASSISTANT, content);
    }
}
