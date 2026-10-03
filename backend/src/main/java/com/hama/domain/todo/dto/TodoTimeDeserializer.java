package com.hama.domain.todo.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 배열·빈 문자열·24:00 자동 보정을 허용하지 않고 명세의 HH:mm만 받습니다. */
public final class TodoTimeDeserializer extends ValueDeserializer<LocalTime> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(LocalTime.class, "시간은 HH:mm 문자열이어야 합니다.");
        }
        String value = parser.getString();
        if (!value.matches("[0-9]{2}:[0-9]{2}")) {
            return context.reportInputMismatch(LocalTime.class, "시간은 HH:mm 형식이어야 합니다.");
        }
        try {
            return LocalTime.parse(value, FORMAT);
        } catch (DateTimeParseException exception) {
            return context.reportInputMismatch(LocalTime.class, "유효한 HH:mm 시간이어야 합니다.");
        }
    }
}
