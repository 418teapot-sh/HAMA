package com.hama.domain.goalai.entity;

/** AI 가 다음 답변으로 기대하는 정보. 프론트가 입력 UI(숫자·날짜 등)를 고르는 데 씁니다. */
public enum Expects {
    CURRENT_LEVEL, PERIOD, AVAILABLE_TIME, OTHER;

    /** AI 가 모르는 값을 주면 OTHER 로 받습니다. */
    public static Expects parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Expects expects : values()) {
            if (expects.name().equalsIgnoreCase(value.strip())) {
                return expects;
            }
        }
        return OTHER;
    }
}
