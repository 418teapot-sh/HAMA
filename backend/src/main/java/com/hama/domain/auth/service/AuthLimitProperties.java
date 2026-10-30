package com.hama.domain.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 app.auth-limit.* 설정값. IP 하나가 15분에 가입·로그인할 수 있는 횟수입니다.
 * 이메일 인증이 없어 가짜 이메일로 무료 크레딧을 계속 받거나, 이메일을 바꿔 가며 로그인 제한을 피하는 걸 막습니다.
 * 통신사·행사장 와이파이는 여러 사람이 IP 하나를 나눠 써서 넉넉하게 둡니다.
 * 테스트는 같은 IP(127.0.0.1)로 수백 번 가입하므로 application-test.yml 에서 크게 올립니다.
 *
 * @param signupPerIp 가입 횟수
 * @param loginPerIp  로그인 시도 횟수(성공 포함). 이메일+IP 기준 5회 제한과 따로 셉니다
 */
@ConfigurationProperties(prefix = "app.auth-limit")
public record AuthLimitProperties(@DefaultValue("30") int signupPerIp, @DefaultValue("50") int loginPerIp) {
}
