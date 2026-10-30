CREATE TABLE schedule_completion
(
    completion_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    occurrence_date DATE NOT NULL,
    display_date DATE NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (completion_id),
    UNIQUE KEY uk_schedule_completion_occurrence_day (schedule_id, occurrence_date, display_date),
    INDEX idx_schedule_completion_user_day (user_id, display_date)
) ENGINE = InnoDB;
