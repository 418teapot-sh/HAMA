-- 팀 ERD 의 MILESTONE / PERIOD_GOAL 입니다. goals/ai 플랜 선택(단계 분해) 때 생성됩니다.
-- 두 테이블에는 deleted_at 이 없습니다. 항상 목표를 통해서만 조회하므로, 목표가 삭제되면 함께 보이지 않습니다.
CREATE TABLE milestone
(
    milestone_id BIGINT       NOT NULL AUTO_INCREMENT,
    goal_id      BIGINT       NOT NULL,
    seq          INT          NOT NULL,
    title        VARCHAR(100) NOT NULL,
    description  TEXT,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    status       VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (milestone_id),
    INDEX idx_milestone_goal_seq (goal_id, seq)
);

CREATE TABLE period_goal
(
    period_goal_id BIGINT       NOT NULL AUTO_INCREMENT,
    goal_id        BIGINT       NOT NULL,
    milestone_id   BIGINT       NOT NULL,
    period_type    VARCHAR(10)  NOT NULL CHECK (period_type IN ('MONTHLY', 'WEEKLY')),
    seq            INT          NOT NULL,
    title          VARCHAR(100) NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (period_goal_id),
    INDEX idx_period_goal_goal_milestone_seq (goal_id, milestone_id, seq)
);
