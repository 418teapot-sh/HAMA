package com.hama.domain.calendar.google.importer;

import java.time.LocalDate;
import java.util.List;

public interface GoogleEventsProvider {
    /** 페이지를 모두 가져오며 1,000건을 넘으면 부분 결과 대신 오류를 반환합니다. */
    List<GoogleCalendarEvent> fetchEvents(String accessToken, String canonicalCalendarId,
            LocalDate from, LocalDate to);
}
