-- 현재 상태(current_status)는 PATCH /goals/{goalId}/current-status 로 계속 바뀌므로, 종료 리뷰가 비교할 시작 상태를 따로 둡니다.
-- 기존 목표는 지금 값을 시작 상태로 옮깁니다(이미 바뀐 값이라도 원래 값은 남아 있지 않습니다).
ALTER TABLE goal
    ADD COLUMN initial_status VARCHAR(200) AFTER title;

UPDATE goal
SET initial_status = current_status;
