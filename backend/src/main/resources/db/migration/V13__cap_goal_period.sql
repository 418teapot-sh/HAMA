-- 목표 기간 상한(시작일 포함 최대 365일, Goal.withinMaxPeriod) 전에 저장된 긴 목표를 상한에 맞춥니다.
-- 종료일은 시작일 + 364일로 자르고, 하위 마일스톤·기간 목표의 날짜도 새 종료일을 넘지 않게 맞춥니다.
-- 하위 데이터는 goal 의 옛 기간으로 골라내므로 goal 은 마지막에 바꿉니다.
-- 새 종료일 뒤에 남은 투두는 그대로 둡니다. replan 이 종료일 안으로 다시 배치합니다.

-- 옛 기간으로 펼친, 아직 선택하지 않은 플랜은 다시 만들어야 합니다.
DELETE p
FROM goal_plan p
         JOIN goal g ON g.goal_id = p.goal_id
WHERE p.selected = b'0'
  AND g.start_date IS NOT NULL
  AND g.end_date IS NOT NULL
  AND DATEDIFF(g.end_date, g.start_date) >= 365;

UPDATE milestone m
    JOIN goal g ON g.goal_id = m.goal_id
SET m.start_date = LEAST(m.start_date, DATE_ADD(g.start_date, INTERVAL 364 DAY)),
    m.end_date   = LEAST(m.end_date, DATE_ADD(g.start_date, INTERVAL 364 DAY))
WHERE g.start_date IS NOT NULL
  AND g.end_date IS NOT NULL
  AND DATEDIFF(g.end_date, g.start_date) >= 365
  AND m.end_date > DATE_ADD(g.start_date, INTERVAL 364 DAY);

UPDATE period_goal pg
    JOIN goal g ON g.goal_id = pg.goal_id
SET pg.start_date = LEAST(pg.start_date, DATE_ADD(g.start_date, INTERVAL 364 DAY)),
    pg.end_date   = LEAST(pg.end_date, DATE_ADD(g.start_date, INTERVAL 364 DAY))
WHERE g.start_date IS NOT NULL
  AND g.end_date IS NOT NULL
  AND DATEDIFF(g.end_date, g.start_date) >= 365
  AND pg.end_date > DATE_ADD(g.start_date, INTERVAL 364 DAY);

UPDATE goal
SET end_date = DATE_ADD(start_date, INTERVAL 364 DAY)
WHERE start_date IS NOT NULL
  AND end_date IS NOT NULL
  AND DATEDIFF(end_date, start_date) >= 365;
