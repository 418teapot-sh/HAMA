package com.hama.domain.payment.service;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 paddle.* 설정값.
 *
 * @param webhookSecret  웹훅 서명 검증 키({@code pdl_ntfset_...}). Paddle 대시보드의 알림 설정(Notification destination)에서
 *                       발급합니다. Sandbox 와 Live 의 값이 다릅니다
 * @param creditPriceIds 충전 횟수 → Paddle 가격 ID. 비어 있는 값(환경변수 미설정)은 무시합니다
 */
@ConfigurationProperties(prefix = "paddle")
public record PaddleProperties(String webhookSecret, Map<Integer, String> creditPriceIds) {

    /** 가격 ID 로 충전 횟수를 찾습니다. 모르는 가격이면 0 입니다. */
    public int creditsOf(String priceId) {
        if (priceId == null || priceId.isBlank() || creditPriceIds == null) {
            return 0;
        }
        return creditPriceIds.entrySet().stream()
                .filter(entry -> priceId.equals(entry.getValue()))
                .mapToInt(Map.Entry::getKey)
                .findFirst()
                .orElse(0);
    }
}
