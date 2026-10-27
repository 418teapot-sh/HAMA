-- 비밀번호 재설정 링크. 사용자당 한 행이라 메일을 다시 보내면 옛 링크는 행이 바뀌어 무효가 됩니다.
-- 링크의 원본 토큰은 메일에만 있고 여기엔 SHA-256 해시만 둡니다(DB 가 유출돼도 링크를 만들 수 없음).
-- 한 번 쓰면 행을 지웁니다. 회원탈퇴 때 함께 지웁니다(UserService.USER_TABLES).
CREATE TABLE password_reset_token
(
    password_reset_token_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT       NOT NULL,
    token_hash              VARCHAR(255) NOT NULL,
    expires_at              DATETIME(6)  NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (password_reset_token_id),
    CONSTRAINT uk_password_reset_token_user_id UNIQUE (user_id),
    CONSTRAINT uk_password_reset_token_token_hash UNIQUE (token_hash)
);

-- 이메일 찾기 문의. 자동으로 찾아주지 않고 운영자가 확인해 답장합니다.
-- 로그인 못 한 사람이 보내는 것이라 users 와 이어지지 않습니다(user_id 없음).
CREATE TABLE email_inquiry
(
    email_inquiry_id BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(50)  NOT NULL,
    reply_email      VARCHAR(255) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (email_inquiry_id)
);
