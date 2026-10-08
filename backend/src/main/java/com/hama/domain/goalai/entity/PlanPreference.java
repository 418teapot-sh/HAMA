package com.hama.domain.goalai.entity;

public enum PlanPreference {
    RELAXED("여유 있게, 꾸준히"),
    BALANCED("여유와 속도의 균형"),
    INTENSIVE("짧고 굵게 집중해서");

    private final String prompt;

    PlanPreference(String prompt) {
        this.prompt = prompt;
    }

    public String prompt() {
        return prompt;
    }
}
