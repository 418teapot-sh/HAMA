-- 일회성 구글 인증과 가져온 일정의 HAMA UID를 사용자별로 보관합니다.
CREATE TABLE google_calendar_import_session
(
    session_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    state_hash VARCHAR(64) NOT NULL,
    verifier_cipher TEXT NOT NULL,
    access_token_cipher TEXT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_google_import_session_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_google_import_session_user (user_id),
    INDEX idx_google_import_session_expiry (expires_at)
);

CREATE TABLE google_calendar_import_source
(
    user_id BIGINT NOT NULL,
    calendar_hash VARCHAR(64) NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    calendar_uid VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_google_import_source_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    PRIMARY KEY (user_id, calendar_hash, event_hash)
);
