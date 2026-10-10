-- 회원가입 약관 동의 기록입니다.
-- 필수 약관(서비스 이용약관·개인정보 수집 및 이용·만 14세 이상)은 동의해야만 가입되므로 따로 저장하지 않습니다.
-- 동의 시각은 가입 시각(created_at)과 같습니다. 마케팅 알림 수신(선택)만 여부를 남깁니다.
ALTER TABLE users
    ADD COLUMN marketing_agreed BIT(1) NOT NULL DEFAULT b'0';
