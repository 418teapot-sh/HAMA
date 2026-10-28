-- A/B 플랜을 마일스톤 없이 평평한 반복 할 일로 단순화해 더 쓰지 않습니다.
-- /api/v1/goals/{goalId}/tree 조회도 함께 없앴고, 투두의 기간 목표 연결(periodGoalId)도 전부 없앴습니다.
DROP TABLE period_goal;
DROP TABLE milestone;

ALTER TABLE todo DROP INDEX idx_todo_period_goal_deleted;
ALTER TABLE todo DROP COLUMN period_goal_id;
