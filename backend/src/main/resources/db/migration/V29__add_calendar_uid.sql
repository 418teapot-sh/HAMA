-- 현재 내보내는 UID를 삭제된 행까지 보존합니다. AI 투두의 캘린더 type은 AI_GOAL입니다.
-- 서로 다른 테이블의 같은 PK도 type 접두어로 구분됩니다.
ALTER TABLE todo ADD COLUMN calendar_uid VARCHAR(80) NULL;
ALTER TABLE schedule ADD COLUMN calendar_uid VARCHAR(80) NULL;

UPDATE todo
SET calendar_uid = CONCAT('hama-', IF(category = 'AI_GOAL_TASK', 'AI_GOAL', category), '-', todo_id, '@hama.app');
UPDATE schedule
SET calendar_uid = CONCAT('hama-', type, '-', schedule_id, '@hama.app');

ALTER TABLE todo MODIFY COLUMN calendar_uid VARCHAR(80) NOT NULL;
ALTER TABLE schedule MODIFY COLUMN calendar_uid VARCHAR(80) NOT NULL;

-- 새 항목은 애플리케이션에서 UUID UID를 발급합니다. 전환은 기존 UID를 이어받으므로
-- 원본 소프트 삭제 행과 새 행이 같은 UID를 가질 수 있어 UNIQUE 제약을 두지 않습니다.
