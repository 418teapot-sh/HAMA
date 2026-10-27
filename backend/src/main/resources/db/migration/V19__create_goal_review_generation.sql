-- #181: AI 호출 전 목표별 예약. 성공 입력/결과 지문은 같은 입력의 무료 재사용에 사용합니다.
-- 기존 goal_review는 그대로 유지하며, 만료 예약은 다음 생성 요청에서 환급·회수합니다.
CREATE TABLE goal_review_generation
(
    generation_id          BIGINT      NOT NULL AUTO_INCREMENT,
    goal_id                BIGINT      NOT NULL,
    request_token          VARCHAR(36),
    request_input_hash     VARCHAR(64),
    lease_until            DATETIME(6),
    charged                BIT(1)      NOT NULL,
    successful_input_hash  VARCHAR(64),
    successful_result_hash VARCHAR(64),
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (generation_id),
    UNIQUE KEY uk_goal_review_generation_goal (goal_id)
);
