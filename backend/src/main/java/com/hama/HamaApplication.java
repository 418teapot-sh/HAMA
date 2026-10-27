package com.hama;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// PasswordAttemptLimiter · 만료된 리프레시 토큰 정리(@Scheduled)에 필요합니다.
@EnableScheduling
// 계정 찾기 메일을 응답과 따로 보냅니다(AccountMailSender).
@EnableAsync
public class HamaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HamaApplication.class, args);
    }

}
