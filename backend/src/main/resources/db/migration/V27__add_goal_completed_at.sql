-- 완료한 목표의 고정 일정은 완료일까지만 보여주고, 완료 목록은 완료한 순으로 정렬합니다.
-- 이미 완료한 목표는 완료 시각을 따로 남기지 않았으므로 마지막 수정 시각으로 채웁니다.
ALTER TABLE goal
    ADD COLUMN completed_at DATETIME NULL AFTER status;

UPDATE goal SET completed_at = updated_at WHERE status = 'COMPLETED';
