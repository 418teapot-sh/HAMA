package com.hama.domain.schedule.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(OutputCaptureExtension.class)
class ScheduleCalendarBackfillTest {
    @Test
    void 실패한_배치도_커서를_옮기고_집계를_남긴다(CapturedOutput output) {
        var index = mock(ScheduleCalendarIndex.class);
        when(index.backfillBatch(0)).thenReturn(new ScheduleCalendarIndex.Batch(100, 100, 0, 100));
        when(index.backfillBatch(100)).thenReturn(new ScheduleCalendarIndex.Batch(102, 2, 1, 0));
        when(index.backfillBatch(102)).thenReturn(new ScheduleCalendarIndex.Batch(102, 0, 0, 0));
        new ScheduleCalendarBackfill(index).run(null);
        verify(index).backfillBatch(102);
        assertThat(output.getAll()).contains("processed=102, succeeded=1, failed=100, skipped=1");
    }

    @Test
    void 후보조회_실패는_기동을_막지않으며_원문_오류메시지는_로그에_노출하지않는다(CapturedOutput output) {
        var index = mock(ScheduleCalendarIndex.class);
        when(index.backfillBatch(0)).thenThrow(new DataAccessResourceFailureException("private-schedule-text"));
        assertThatCode(() -> new ScheduleCalendarBackfill(index).run(null)).doesNotThrowAnyException();
        verify(index, times(1)).backfillBatch(0);
        assertThat(output.getAll()).contains("backfill stopped", "DataAccessResourceFailureException")
                .doesNotContain("private-schedule-text");
    }
}
