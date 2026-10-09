-- 탈퇴(user_id 로 전부 삭제)와 재발급·로그아웃(user_id + token_hash)이 테이블 전체를 훑지 않게 합니다.
-- 전체 스캔 DELETE/UPDATE 는 훑은 행을 모두 잠가서, 그동안 다른 사용자의 로그인·재발급이 기다립니다.
ALTER TABLE refresh_token
    ADD INDEX idx_refresh_token_user (user_id);
