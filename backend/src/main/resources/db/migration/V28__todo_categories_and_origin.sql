-- 기존 목표 투두의 출처는 추측하지 않습니다. 새 생성은 서버가 MANUAL/AI를 기록합니다.
ALTER TABLE todo
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        CHECK (origin IN ('MANUAL', 'AI', 'LEGACY_UNKNOWN')) AFTER category;
UPDATE todo SET origin = 'MANUAL' WHERE category = 'TASK';

-- MySQL이 붙인 CHECK 이름에 의존하지 않고 category CHECK만 교체합니다.
-- status 등 다른 CHECK는 보존합니다.
SET @todo_category_drops = (
    SELECT GROUP_CONCAT(CONCAT('DROP CHECK `', REPLACE(tc.CONSTRAINT_NAME, '`', '``'), '`') SEPARATOR ', ')
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'todo' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE REGEXP '(^|[^a-z_])category([^a-z_]|$)'
);
SET @todo_category_sql = CONCAT('ALTER TABLE todo ',
    IF(@todo_category_drops IS NULL, '', CONCAT(@todo_category_drops, ', ')),
    'ADD CONSTRAINT chk_todo_category CHECK (category IN (''TASK'', ''OTHER'', ''AI_GOAL_TASK''))');
PREPARE todo_category_statement FROM @todo_category_sql;
EXECUTE todo_category_statement;
DEALLOCATE PREPARE todo_category_statement;
