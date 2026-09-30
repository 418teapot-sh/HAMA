CREATE TABLE users
(
    user_id            BIGINT       NOT NULL AUTO_INCREMENT,
    email              VARCHAR(255) NOT NULL,
    password           VARCHAR(255) NOT NULL,
    nickname           VARCHAR(30)  NOT NULL,
    is_premium         BIT(1)       NOT NULL,
    goal_created_count INT          NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 로그인한 기기마다 한 행입니다. user_id 는 unique 가 아닙니다(여러 기기 허용).
CREATE TABLE refresh_token
(
    refresh_token_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    token_hash       VARCHAR(255) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (refresh_token_id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_token_updated_at (updated_at)
);
