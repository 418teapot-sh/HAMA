-- goals/ai 가 PLANNING 목표에 만들어 주는 A/B 플랜입니다. 마일스톤·주간 목표·날짜별 투두로 펼친 결과를
-- detail_json 에 앱이 JSON 문자열로 저장합니다(투두가 많으면 64KB 를 넘을 수 있어 MEDIUMTEXT).
-- 선택하면 selected = 1 이고 detail_json 대로 milestone / period_goal / todo 를 만듭니다.
CREATE TABLE goal_plan
(
    plan_id           BIGINT       NOT NULL AUTO_INCREMENT,
    goal_id           BIGINT       NOT NULL,
    variant           VARCHAR(1)   NOT NULL CHECK (variant IN ('A', 'B')),
    title             VARCHAR(50)  NOT NULL,
    summary           VARCHAR(255) NOT NULL,
    preference        VARCHAR(20)  NOT NULL CHECK (preference IN ('RELAXED', 'BALANCED', 'INTENSIVE')),
    detail_json       MEDIUMTEXT   NOT NULL,
    total_todos       INT          NOT NULL,
    avg_daily_minutes INT          NOT NULL,
    selected          BIT(1)       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (plan_id),
    INDEX idx_goal_plan_goal (goal_id)
) ENGINE = InnoDB;
