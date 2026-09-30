package com.hama.domain.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 paddle.* 설정값.
 *
 * @param webhookSecret 웹훅 서명 검증 키({@code pdl_ntfset_...}). Paddle 대시보드의 알림 설정(Notification destination)에서
 *                      발급합니다. Sandbox 와 Live 의 값이 다릅니다
 */
@ConfigurationProperties(prefix = "paddle")
public record PaddleProperties(String webhookSecret) {
}
