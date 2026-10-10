package com.hama.domain.checkin.entity;

import tools.jackson.core.JsonToken;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.DeserializationContext;

public enum CheckinType {
    START, MID, END;

    public static final class Deserializer extends ValueDeserializer<CheckinType> {
        @Override
        public CheckinType deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                try {
                    return CheckinType.valueOf(parser.getString());
                } catch (IllegalArgumentException ignored) {
                    // enum 순번이나 소문자를 자동 변환하지 않습니다.
                }
            }
            return context.reportInputMismatch(CheckinType.class, "START, MID, END 문자열을 입력해야 합니다.");
        }
    }
}
