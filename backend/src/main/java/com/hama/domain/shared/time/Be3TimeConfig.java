package com.hama.domain.shared.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Be3TimeConfig {

    @Bean("be3Clock")
    Clock be3Clock() {
        return Clock.system(Be3Time.KST);
    }
}
