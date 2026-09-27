-- 테스트 전용 DB. 개발용 hama 데이터를 테스트가 지우지 않도록 분리합니다.
CREATE DATABASE IF NOT EXISTS hama_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON hama_test.* TO 'hama'@'%';
FLUSH PRIVILEGES;
