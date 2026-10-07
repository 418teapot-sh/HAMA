-- 팀 ERD 의 GOAL 테이블입니다.
-- status 에는 PLANNING / IN_PROGRESS 만 저장합니다. PAST 는 end_date < 오늘(KST) 로 조회 시점에 계산합니다.
CREATE TABLE goal
(
    goal_id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                BIGINT        NOT NULL,
    title                  VARCHAR(100)  NOT NULL,
    description            TEXT,
    metric_name            VARCHAR(50),
    unit                   VARCHAR(20),
    start_value            DECIMAL(10, 2),
    target_value           DECIMAL(10, 2),
    weekly_available_hours DECIMAL(4, 1),
    current_level          TEXT,
    reality_verdict        VARCHAR(20),
    reality_comment        TEXT,
    result_status          VARCHAR(20),
    start_date             DATE,
    end_date               DATE,
    status                 VARCHAR(20)   NOT NULL CHECK (status IN ('PLANNING', 'IN_PROGRESS')),
    deleted_at             DATETIME(6),
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (goal_id),
    INDEX idx_goal_user_deleted (user_id, deleted_at)
);
