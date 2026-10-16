-- 목표 종료 AI 리뷰입니다. 목표당 1건이고, 다시 만들면 덮어씁니다(goal_id unique).
CREATE TABLE goal_review
(
    review_id      BIGINT       NOT NULL AUTO_INCREMENT,
    goal_id        BIGINT       NOT NULL,
    achieved       BIT(1)       NOT NULL,
    comparison     VARCHAR(500) NOT NULL,
    message        VARCHAR(500) NOT NULL,
    current_status VARCHAR(200),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (review_id),
    UNIQUE KEY uk_goal_review_goal (goal_id)
);
