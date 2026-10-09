-- 회원가입 약관 동의 기록입니다.
-- 필수 약관(서비스 이용약관·개인정보 수집 및 이용·만 14세 이상)은 항상 동의라 여부 대신 동의한 시각을 남깁니다.
-- 마케팅 알림 수신은 선택이라 여부와 시각을 남깁니다.
ALTER TABLE users
    ADD COLUMN terms_agreed_at     DATETIME(6) NULL,
    ADD COLUMN marketing_agreed    BIT(1)      NOT NULL DEFAULT b'0',
    ADD COLUMN marketing_agreed_at DATETIME(6) NULL;

-- 이 컬럼 전에 가입한 사용자는 가입 화면에서 필수 약관에 동의했으므로 가입 시각으로 채웁니다.
UPDATE users SET terms_agreed_at = created_at;

ALTER TABLE users
    MODIFY COLUMN terms_agreed_at DATETIME(6) NOT NULL;
