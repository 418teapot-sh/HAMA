package com.hama.domain.schedule.index;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 신규 마이그레이션은 컬럼만 추가합니다. 재시작 시 미처리/변경된 원본만 이어서 보정합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleCalendarBackfill implements ApplicationRunner {
    private final ScheduleCalendarIndex index;

    @Override
    public void run(ApplicationArguments arguments) {
        long cursor = 0;
        long processed = 0;
        long succeeded = 0;
        long failed = 0;
        while (true) {
            ScheduleCalendarIndex.Batch batch;
            try {
                batch = index.backfillBatch(cursor);
            } catch (RuntimeException exception) {
                log.error("Schedule calendar index backfill stopped: cursor={}, processed={}, succeeded={}, failed={}, errorType={}",
                        cursor, processed, succeeded, failed, exception.getClass().getSimpleName());
                return;
            }
            if (batch.count() == 0) break;
            cursor = batch.lastId();
            processed += batch.count();
            succeeded += batch.succeeded();
            failed += batch.failed();
        }
        log.info("Schedule calendar index backfill completed: processed={}, succeeded={}, failed={}, skipped={}",
                processed, succeeded, failed, processed - succeeded - failed);
    }
}
