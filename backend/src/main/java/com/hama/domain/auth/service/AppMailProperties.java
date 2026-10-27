package com.hama.domain.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.mail.* 설정값. SMTP 접속 정보는 spring.mail.* 에 있습니다.
 *
 * @param from             보내는 사람. Resend 에 인증한 도메인 주소여야 스팸으로 빠지지 않습니다
 * @param resetPasswordUrl 재설정 링크 앞부분(프론트 화면). 뒤에 {@code ?token=} 이 붙습니다
 * @param operator         이메일 찾기 문의 알림을 받을 운영자 주소
 * @param logBody          RESEND_API_KEY 가 없을 때 메일 본문(재설정 링크)을 로그에 남길지. 로컬에서만 true 입니다
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(String from, String resetPasswordUrl, String operator, boolean logBody) {
}
