package com.hama;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// LoginAttemptLimiter 의 만료 항목 정리(@Scheduled)에 필요합니다.
@EnableScheduling
public class HamaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HamaApplication.class, args);
    }

}
