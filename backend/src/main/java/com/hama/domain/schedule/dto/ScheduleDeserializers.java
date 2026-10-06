package com.hama.domain.schedule.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 명세의 문자열·boolean·KST 일시 형식을 강제하며 자동 형변환을 허용하지 않습니다. */
public final class ScheduleDeserializers {

    private ScheduleDeserializers() {
    }

    public static final class Text extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(String.class, "문자열을 입력해야 합니다.");
            }
            return parser.getString();
        }
    }

    public static final class Bool extends ValueDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (parser.hasToken(JsonToken.VALUE_TRUE)) {
                return true;
            }
            if (parser.hasToken(JsonToken.VALUE_FALSE)) {
                return false;
            }
            return context.reportInputMismatch(Boolean.class, "true 또는 false를 입력해야 합니다.");
        }
    }

    public static final class DateTime extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(LocalDateTime.class, "일시는 YYYY-MM-DDTHH:mm:ss 문자열이어야 합니다.");
            }
            String value = parser.getString();
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}")) {
                return context.reportInputMismatch(LocalDateTime.class, "일시는 YYYY-MM-DDTHH:mm:ss 형식이어야 합니다.");
            }
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException exception) {
                return context.reportInputMismatch(LocalDateTime.class, "유효한 YYYY-MM-DDTHH:mm:ss 일시여야 합니다.");
            }
        }
    }
}
