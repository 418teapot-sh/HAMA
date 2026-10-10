-- 팀 ERD 의 TODO.goal_id / period_goal_id 입니다. AI_GOAL_TASK 일 때만 채워지고 일반 TASK 는 NULL 입니다.
-- Todo 엔티티는 아직 이 컬럼을 매핑하지 않습니다(ddl-auto: validate 는 매핑 안 된 컬럼을 문제 삼지 않습니다).
-- 목표 진행률·트리 집계는 goal 도메인의 GoalTodoRepository 가 이 컬럼으로 직접 조회합니다.
ALTER TABLE todo
    ADD COLUMN goal_id        BIGINT NULL AFTER user_id,
    ADD COLUMN period_goal_id BIGINT NULL AFTER goal_id,
    ADD INDEX idx_todo_goal_deleted (goal_id, deleted_at),
    ADD INDEX idx_todo_period_goal_deleted (period_goal_id, deleted_at);
