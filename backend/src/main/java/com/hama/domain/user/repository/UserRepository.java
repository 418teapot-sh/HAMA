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

    /** 결제 완료 시 충전합니다. 읽고 더해서 저장하면 동시에 일어난 차감을 덮어쓰므로 DB 에서 더합니다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.credits = u.credits + :amount WHERE u.id = :id")
    int addCredits(Long id, int amount);
}
