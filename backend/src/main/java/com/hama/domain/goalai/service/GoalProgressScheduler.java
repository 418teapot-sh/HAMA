package com.hama.domain.goalai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.goal-progress.worker-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class GoalProgressScheduler {

    private final GoalProgressService service;

    @Scheduled(fixedDelayString = "${app.goal-progress.poll-ms:5000}")
    public void refresh() {
        try {
            service.processDue();
        } catch (RuntimeException failure) {
            log.warn("[goal-progress] poll failed error={}", failure.getClass().getSimpleName());
        }
    }
}
