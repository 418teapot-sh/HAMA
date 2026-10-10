-- goals/ai 대화 세션과 메시지입니다. 초안(draft_json)과 현실성 결과(reality_json)는 앱이 JSON 문자열로 저장합니다.
-- 확정되면 status = CONFIRMED 이고 goal_id 에 만든 목표를 기록합니다.
CREATE TABLE goal_ai_session
(
    session_id   BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    raw_goal     TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('COLLECTING', 'READY', 'CONFIRMED')),
    draft_json   TEXT,
    reality_json TEXT,
    goal_id      BIGINT,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id),
    INDEX idx_goal_ai_session_user (user_id)
) ENGINE = InnoDB;

CREATE TABLE goal_ai_message
(
    message_id BIGINT      NOT NULL AUTO_INCREMENT,
    session_id BIGINT      NOT NULL,
    role       VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'AI')),
    content    TEXT        NOT NULL,
    expects    VARCHAR(20) CHECK (expects IN ('CURRENT_LEVEL', 'PERIOD', 'AVAILABLE_TIME', 'OTHER')),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (message_id),
    INDEX idx_goal_ai_message_session (session_id, message_id)
) ENGINE = InnoDB;
