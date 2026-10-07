package com.hama.domain.todo.dto;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 숫자나 boolean이 투두 내용/메모 문자열로 조용히 바뀌지 않게 합니다. */
public final class TodoStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(String.class, "문자열을 입력해야 합니다.");
        }
        return parser.getString();
    }
}
