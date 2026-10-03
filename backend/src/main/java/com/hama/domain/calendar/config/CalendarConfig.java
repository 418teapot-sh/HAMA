package com.hama.domain.calendar.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalendarConfig {
    @Bean("calendarClock")
    Clock calendarClock() {
        return Clock.systemUTC();
    }
}
