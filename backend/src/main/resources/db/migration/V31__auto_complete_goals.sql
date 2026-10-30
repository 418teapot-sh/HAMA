-- 기존 수동 완료는 유지하고, 이미 투두를 모두 마친 진행 중 목표도 자동 완료합니다.
ALTER TABLE goal ADD COLUMN auto_completed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE goal g
JOIN (
    SELECT goal_id, MAX(completed_at) AS last_completed_at
    FROM todo
    WHERE category = 'AI_GOAL_TASK' AND deleted_at IS NULL AND goal_id IS NOT NULL
    GROUP BY goal_id
    HAVING COUNT(*) = SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END)
) t ON t.goal_id = g.goal_id
SET g.status = 'COMPLETED', g.auto_completed = TRUE,
    g.completed_at = COALESCE(t.last_completed_at, g.updated_at)
WHERE g.deleted_at IS NULL AND g.status = 'IN_PROGRESS'
  AND g.end_date >= DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR);
