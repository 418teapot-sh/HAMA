package com.hama.domain.goalai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 세션 초안·현실성 결과·플랜 상세를 TEXT 컬럼의 JSON 문자열로 바꿉니다. 저장한 값만 읽으므로 실패는 버그입니다. */
@Component
@RequiredArgsConstructor
public class GoalAiJson {

    private final JsonMapper jsonMapper;

    public String write(Object value) {
        return value == null ? null : jsonMapper.writeValueAsString(value);
    }

    public <T> T read(String json, Class<T> type) {
        return json == null ? null : jsonMapper.readValue(json, type);
    }
}
