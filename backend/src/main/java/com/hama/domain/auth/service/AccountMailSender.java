package com.hama.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 계정 찾기 메일을 보냅니다. 응답을 기다리게 하지 않으려고 따로 스레드에서 보내고(@Async), 실패해도 요청은 성공으로 끝납니다.
 * 비밀번호 재설정은 가입된 이메일일 때만 메일을 보내는데, 보내는 시간(SMTP 1~2초)만큼 응답이 늦어지면
 * 응답 시간으로 가입 여부가 드러나서 비동기가 필요합니다.
 *
 * <p>RESEND_API_KEY 가 없으면(로컬·테스트) 메일 대신 내용을 로그로 남깁니다. 로컬에선 로그의 링크로 재설정을 해볼 수 있습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMailSender {

    private final JavaMailSender mailSender;
    private final AppMailProperties properties;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Async
    public void sendPasswordReset(String to, String rawToken) {
        String link = properties.resetPasswordUrl() + "?token=" + rawToken;
        send(to, "[HAMA] 비밀번호 재설정 링크", """
                안녕하세요, HAMA 입니다.

                아래 링크에서 새 비밀번호를 설정해주세요. 링크는 30분 동안 한 번만 쓸 수 있어요.
                %s

                비밀번호 찾기를 요청한 적이 없다면 이 메일은 무시하셔도 됩니다. 비밀번호는 바뀌지 않아요.
                """.formatted(link));
    }

    @Async
    public void sendEmailInquiryNotice(String name, String replyEmail) {
        send(properties.operator(), "[HAMA] 이메일 찾기 문의", """
                이메일 찾기 문의가 들어왔습니다. 가입 정보를 확인해 답변 이메일로 안내해주세요.

                이름: %s
                답변받을 이메일: %s
                """.formatted(name, replyEmail));
    }

    private void send(String to, String subject, String text) {
        if (smtpPassword == null || smtpPassword.isBlank()) {
            log.warn("[Mail] RESEND_API_KEY 가 없어 메일을 보내지 않습니다. to={} subject={}\n{}", to, subject, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
            log.info("[Mail] 발송 subject={}", subject);
        } catch (MailException e) {
            // 받는 주소는 개인정보라 남기지 않습니다. 실패해도 사용자는 다시 요청할 수 있습니다.
            log.error("[Mail] 발송 실패 subject={}", subject, e);
        }
    }
}
