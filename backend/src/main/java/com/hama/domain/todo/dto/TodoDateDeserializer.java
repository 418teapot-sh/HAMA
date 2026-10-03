package com.hama.domain.todo.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class TodoDateDeserializer extends ValueDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(LocalDate.class, "날짜는 YYYY-MM-DD 문자열이어야 합니다.");
        }
        String value = parser.getString();
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            return context.reportInputMismatch(LocalDate.class, "날짜는 YYYY-MM-DD 형식이어야 합니다.");
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            return context.reportInputMismatch(LocalDate.class, "유효한 YYYY-MM-DD 날짜여야 합니다.");
        }
    }
}
