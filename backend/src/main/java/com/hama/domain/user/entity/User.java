package com.hama.domain.user.entity;

import com.hama.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// user 는 여러 DB 에서 예약어라 복수형으로 둡니다.
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseTimeEntity {

    /** 가입하면 주는 무료 크레딧(랜딩 "첫 1회는 무료"). */
    public static final int SIGNUP_FREE_CREDITS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** 소문자로 정규화된 값만 들어옵니다(SignupRequest 참고). 동시 가입은 unique 제약이 최종 방어선입니다. */
    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt 해시. 원문은 절대 저장하지 않습니다. */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 남은 크레딧(횟수). AI 목표 생성·일정 재계산에 1회씩 씁니다.
     * 충전·차감은 동시에 일어날 수 있어서 엔티티 값을 고치지 않고 UserRepository 의 UPDATE 로만 바꿉니다.
     */
    @Column(nullable = false)
    private int credits;

    /**
     * 마케팅 알림 수신 동의(선택). 필수 약관은 동의해야만 가입되므로(SignupRequest) 따로 저장하지 않고,
     * 동의 시각은 가입 시각(createdAt)입니다.
     */
    @Column(nullable = false)
    private boolean marketingAgreed;

    /**
     * 회원가입으로 생성합니다.
     * 가입 시점의 불변식(무료 크레딧 1회)을 여기서 강제해서
     * 호출부가 실수로 다른 값을 넣을 여지를 없앱니다.
     *
     * @param encodedPassword 반드시 인코딩된 비밀번호. 원문을 넘기지 마세요.
     */
    public static User create(String email, String encodedPassword, String name, boolean marketingAgreed) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .credits(SIGNUP_FREE_CREDITS)
                .marketingAgreed(marketingAgreed)
                .build();
    }
}
