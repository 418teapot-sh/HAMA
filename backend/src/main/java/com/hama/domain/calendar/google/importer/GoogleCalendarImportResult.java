package com.hama.domain.calendar.google.importer;

import java.util.Map;

public record GoogleCalendarImportResult(int importedCount, int duplicateCount, int skippedCount,
        Map<SkipReason, Integer> skippedReasons) {
    public GoogleCalendarImportResult {
        skippedReasons = Map.copyOf(skippedReasons);
    }

    public enum SkipReason {
        CANCELLED, MISSING_EVENT_ID, INVALID_TITLE, INVALID_TIME, MEMO_TOO_LONG, OUTSIDE_RANGE
    }
}
