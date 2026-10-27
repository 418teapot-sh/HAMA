package com.hama.global.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * @Async 스레드에도 요청의 MDC(TraceIdFilter 의 traceId)를 옮겨 붙입니다. 없으면 비동기로 보낸 메일의 실패 로그가
 * 어느 요청에서 왔는지 traceId 로 찾을 수 없습니다. Spring Boot 가 이 빈을 기본 실행기(applicationTaskExecutor)에 붙입니다.
 */
@Configuration
public class AsyncConfig {

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
