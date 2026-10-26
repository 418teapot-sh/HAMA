package com.hama.domain.user.repository;

import com.hama.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 로그인용. 탈퇴와 겹치지 않도록 기본키로 공유 락(FOR SHARE)을 잡습니다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForShare(Long id);

    /** 탈퇴용. 같은 사용자의 탈퇴·로그인을 한 줄로 세웁니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    boolean existsByEmail(String email);

    /*
     * 크레딧은 아래 세 UPDATE 로만 바꿉니다. 호출한 쪽 트랜잭션(플랜 선택·replan)이 들고 있는 목표·투두를 떼어 내지 않도록
     * clearAutomatically 는 쓰지 않습니다. 그래서 같은 트랜잭션에서 이미 읽은 User 의 credits 는 옛값이니 다시 읽지 마세요.
     */

    /** 결제 완료 시 충전합니다. 읽고 더해서 저장하면 동시에 일어난 차감을 덮어쓰므로 DB 에서 더합니다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.credits = u.credits + :amount WHERE u.id = :id")
    int addCredits(Long id, int amount);

    /** 1회를 씁니다. 남은 게 없으면 0 행이 바뀝니다. 동시에 두 번 불러도 0 아래로 내려가지 않습니다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.credits = u.credits - 1 WHERE u.id = :id AND u.credits > 0")
    int useCredit(Long id);

    /** 환불 시 회수합니다. 남은 것보다 많으면 0 에서 멈춥니다(이미 쓴 크레딧은 되돌릴 수 없음). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.credits = CASE WHEN u.credits > :amount THEN u.credits - :amount ELSE 0 END WHERE u.id = :id")
    int takeBackCredits(Long id, int amount);
}
