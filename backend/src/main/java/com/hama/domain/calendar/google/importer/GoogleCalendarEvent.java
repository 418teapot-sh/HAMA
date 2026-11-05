package com.hama.domain.calendar.google.importer;

/** Google 원본을 변경하지 않고 가져오기 검증에 필요한 필드만 읽습니다. */
public record GoogleCalendarEvent(String id, String status, String summary, String description,
        EventTime start, EventTime end) {
    public record EventTime(String date, String dateTime, String timeZone) {
        public EventTime(String date, String dateTime) { this(date, dateTime, null); }
    }
}
