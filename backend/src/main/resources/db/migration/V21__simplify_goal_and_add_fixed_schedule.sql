-- 목표 생성 플로우 개편: AI 대화 없이 title/currentStatus/기간/고정 일정을 직접 받습니다.
-- 측정 지표(metric_name 등)·AI 대화 산출물(current_level/reality_verdict/reality_comment)은 더 쓰지 않아 지웁니다.
ALTER TABLE goal
    DROP COLUMN description,
    DROP COLUMN metric_name,
    DROP COLUMN unit,
    DROP COLUMN start_value,
    DROP COLUMN target_value,
    DROP COLUMN weekly_available_hours,
    DROP COLUMN current_level,
    DROP COLUMN reality_verdict,
    DROP COLUMN reality_comment,
    ADD COLUMN current_status VARCHAR(200) AFTER title;

-- 목표를 만들 때 함께 받는 "이미 바쁜 요일" 맥락(예: 월요일 - 한국사 공부). A/B 플랜 생성 AI 프롬프트에 참고로만 넘기고,
-- 실제 캘린더(schedule)는 막지 않습니다. 목표가 지워져도 goal_id 로만 이어져 있어 함께 지울 때 goal_id 로 직접 지웁니다.
CREATE TABLE goal_fixed_schedule
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    goal_id    BIGINT       NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    content    VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_goal_fixed_schedule_goal (goal_id)
);
