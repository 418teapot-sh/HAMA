-- build/generated-schema.sql의 새 컬럼/인덱스를 기존 schedule에 추가합니다.
-- 기존 V3와 원본 일정은 변경하지 않습니다. 앱이 미처리 행을 잠근 뒤 파생값만 보정합니다.
-- snapshot이 원본과 다르면 조회에서 제외하지 않으므로 보정 전/구버전 수정도 안전합니다.
ALTER TABLE schedule
    ADD COLUMN calendar_last_end_epoch_second BIGINT NULL,
    ADD COLUMN calendar_source_start_at DATETIME(6) NULL,
    ADD COLUMN calendar_source_end_at DATETIME(6) NULL,
    ADD COLUMN calendar_source_all_day BIT NULL,
    ADD COLUMN calendar_source_repeat_rule VARCHAR(255) NULL,
    ADD INDEX idx_schedule_calendar_end
        (user_id, deleted_at, type, calendar_last_end_epoch_second, schedule_id);
