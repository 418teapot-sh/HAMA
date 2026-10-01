package com.hama.global.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 테스트 컨텍스트가 뜰 때 hama_test 를 비우고 V1 부터 다시 적용합니다.
 * 이전 실행의 데이터가 남아 있으면 CI(매번 빈 DB)에서는 통과하는 테스트가 로컬에서만 깨질 수 있습니다.
 * (예전 ddl-auto: create-drop 과 같은 효과. clean 은 application-test.yml 에서만 허용합니다)
 */
@Configuration
@Profile("test")
class FlywayCleanTestConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
