-- 진행 중인 AI 목표를 사용자가 직접 완료(COMPLETED)할 수 있게 합니다. PAST 는 여전히 저장하지 않고 종료일로 계산합니다.
-- V4 의 이름 없는 컬럼 CHECK 는 MySQL 이 goal_chk_1 로 이름을 붙였습니다.
ALTER TABLE goal DROP CHECK goal_chk_1;
ALTER TABLE goal
    ADD CONSTRAINT chk_goal_status CHECK (status IN ('PLANNING', 'IN_PROGRESS', 'COMPLETED'));
