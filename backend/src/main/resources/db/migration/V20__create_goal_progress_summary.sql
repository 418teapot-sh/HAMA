-- #183: 상태 원문은 todo.status_note에 보존하고, 목표별 요약과 영속 갱신 대기 상태만 저장합니다.
CREATE TABLE goal_progress_summary
(
    summary_id         BIGINT        NOT NULL AUTO_INCREMENT,
    goal_id            BIGINT        NOT NULL,
    revision           BIGINT        NOT NULL,
    processed_revision BIGINT        NOT NULL,
    attempts           INT           NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    next_attempt_at    DATETIME(6)   NOT NULL,
    request_token      VARCHAR(36),
    lease_until        DATETIME(6),
    source_hash        VARCHAR(64),
    summary            VARCHAR(1000),
    generated_at       DATETIME(6),
    used_record_count  INT           NOT NULL,
    total_record_count BIGINT        NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (summary_id),
    UNIQUE KEY uk_goal_progress_summary_goal (goal_id),
    KEY idx_goal_progress_summary_due (next_attempt_at, summary_id)
);
