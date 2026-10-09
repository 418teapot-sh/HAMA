package com.hama.domain.user.repository;

import com.hama.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 로그인용. 탈퇴와 겹치지 않도록 공유 락(FOR SHARE)으로 읽습니다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForShare(String email);

    /** 탈퇴용. 같은 사용자의 탈퇴·로그인을 한 줄로 세웁니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    boolean existsByEmail(String email);
}
