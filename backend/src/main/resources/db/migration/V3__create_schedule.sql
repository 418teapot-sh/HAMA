-- local bootRun의 build/generated-schema.sql에서 schedule 테이블과 인덱스를 추출했습니다.
CREATE TABLE schedule
(
    schedule_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('FIXED', 'PERSONAL')),
    title       VARCHAR(100) NOT NULL,
    start_at    DATETIME(6)  NOT NULL,
    end_at      DATETIME(6)  NOT NULL,
    all_day     BIT          NOT NULL,
    repeat_rule VARCHAR(255),
    memo        TEXT,
    deleted_at  DATETIME(6),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (schedule_id),
    INDEX idx_schedule_user_start_deleted (user_id, start_at, deleted_at)
) ENGINE = InnoDB;
