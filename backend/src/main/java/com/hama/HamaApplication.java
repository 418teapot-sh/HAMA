package com.hama;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// PasswordAttemptLimiter · 만료된 리프레시 토큰 정리(@Scheduled)에 필요합니다.
@EnableScheduling
public class HamaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HamaApplication.class, args);
    }

}
