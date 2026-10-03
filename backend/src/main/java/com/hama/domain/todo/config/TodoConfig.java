package com.hama.domain.todo.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TodoConfig {

    @Bean("todoClock")
    Clock todoClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
