package com.hama.domain.calendar.google.oauth;

public record GoogleCalendarAccount(String id, String summary, String timeZone, boolean primary, String accessRole) {
}
