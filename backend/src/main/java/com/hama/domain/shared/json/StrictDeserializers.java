package com.hama.domain.shared.json;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** BE3 요청의 문자열·boolean·날짜·시간 형식을 강제하며 자동 형변환을 허용하지 않습니다. */
public final class StrictDeserializers {

    private StrictDeserializers() {
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

    public static final class Date extends ValueDeserializer<LocalDate> {
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

    public static final class Time extends ValueDeserializer<LocalTime> {
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
