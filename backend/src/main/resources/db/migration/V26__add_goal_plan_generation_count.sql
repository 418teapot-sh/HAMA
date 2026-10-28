-- A/B 플랜 생성은 크레딧을 쓰지 않으므로 같은 목표로 AI 를 계속 부르지 못하게 목표당 성공 횟수를 셉니다.
ALTER TABLE goal
    ADD COLUMN plan_generation_count INT NOT NULL DEFAULT 0;
