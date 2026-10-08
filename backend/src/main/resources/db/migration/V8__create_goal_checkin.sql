-- local bootRun의 build/generated-schema.sql에서 테이블 정의를 가져왔습니다.
-- MID는 여러 회 허용하고 START/END는 목표당 하나만 허용합니다.
CREATE TABLE goal_checkin
(
    checkin_id BIGINT NOT NULL AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('START', 'MID', 'END')),
    value DECIMAL(10, 2),
    note TEXT,
    achieved BIT,
    checked_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    singleton_type VARCHAR(10) GENERATED ALWAYS AS
        (CASE WHEN type IN ('START', 'END') THEN type ELSE NULL END) STORED,
    PRIMARY KEY (checkin_id),
    UNIQUE KEY uk_checkin_goal_singleton (goal_id, singleton_type),
    INDEX idx_checkin_goal_time (goal_id, checked_at, checkin_id),
    CONSTRAINT chk_checkin_achieved CHECK (type = 'END' OR achieved IS NULL)
) ENGINE = InnoDB;
