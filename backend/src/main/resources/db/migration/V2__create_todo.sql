-- 일반 TASK 저장소. 목표/기간 목표 연결은 BE2 계약 확정 후 별도 마이그레이션으로 추가합니다.
-- local bootRun의 build/generated-schema.sql에서 todo 부분만 추출했습니다.
CREATE TABLE todo
(
    todo_id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    category        VARCHAR(20)  NOT NULL CHECK (category IN ('TASK', 'AI_GOAL_TASK')),
    content         VARCHAR(255) NOT NULL,
    todo_date       DATE         NOT NULL,
    start_time      TIME(0),
    end_time        TIME(0),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
    status_note     TEXT,
    postponed_count INT          NOT NULL,
    completed_at    DATETIME(6),
    deleted_at      DATETIME(6),
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (todo_id),
    INDEX idx_todo_user_date_deleted (user_id, todo_date, deleted_at)
);
