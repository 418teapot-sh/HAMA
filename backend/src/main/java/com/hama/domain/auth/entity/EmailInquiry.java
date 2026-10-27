package com.hama.domain.auth.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일 찾기 문의. 자동으로 찾아주지 않고 운영자가 가입 정보를 확인해 {@code replyEmail} 로 답장합니다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailInquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_inquiry_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private String replyEmail;

    public static EmailInquiry create(String name, String replyEmail) {
        EmailInquiry inquiry = new EmailInquiry();
        inquiry.name = name;
        inquiry.replyEmail = replyEmail;
        return inquiry;
    }
}
