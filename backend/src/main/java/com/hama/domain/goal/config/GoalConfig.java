package com.hama.domain.goal.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoalConfig {

    /** PAST 판정의 "오늘" 은 서버 시간대와 무관하게 KST 기준입니다. 테스트는 @TestBean 으로 고정합니다. */
    @Bean("goalClock")
    Clock goalClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
