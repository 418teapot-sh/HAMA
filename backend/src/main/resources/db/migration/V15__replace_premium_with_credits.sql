-- 결제가 "프리미엄 켜기"에서 횟수제 크레딧으로 바뀌었습니다(10/09 팀 결정).
-- 가입하면 1회를 무료로 주므로, 이미 가입한 사용자에게도 1회를 넣어 둡니다.
ALTER TABLE users
    ADD COLUMN credits INT NOT NULL DEFAULT 0;

UPDATE users
SET credits = 1;

ALTER TABLE users
    DROP COLUMN is_premium,
    DROP COLUMN goal_created_count;
