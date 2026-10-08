package com.hama.domain.calendar.dto;

import com.hama.domain.calendar.exception.CalendarErrorCode;
import com.hama.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

public record CalendarQuery(LocalDate from, LocalDate to, Set<CalendarType> types) {

    public CalendarQuery {
        if (from == null || to == null || from.getYear() < 1000 || to.getYear() > 9999
                || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new BusinessException(CalendarErrorCode.CALENDAR_INVALID_RANGE);
        }
        types = Set.copyOf(types);
    }

    public static CalendarQuery parse(String from, String to, String types) {
        Set<CalendarType> selected = EnumSet.allOf(CalendarType.class);
        if (types != null) {
            selected = EnumSet.noneOf(CalendarType.class);
            try {
                for (String value : types.split(",", -1)) {
                    selected.add(CalendarType.valueOf(value.trim()));
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(CalendarErrorCode.CALENDAR_INVALID_TYPES);
            }
        }
        return new CalendarQuery(date(from), date(to), selected);
    }

    private static LocalDate date(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new BusinessException(CalendarErrorCode.CALENDAR_INVALID_RANGE);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CalendarErrorCode.CALENDAR_INVALID_RANGE);
        }
    }
}
