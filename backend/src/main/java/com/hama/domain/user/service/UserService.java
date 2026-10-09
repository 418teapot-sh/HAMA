package com.hama.domain.user.service;

import com.hama.domain.auth.service.LoginAttemptLimiter;
import com.hama.domain.user.dto.UserResponse;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.exception.UserErrorCode;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /**
     * 탈퇴 시 지우는 순서입니다. 테이블 사이에 FK 가 없어서 DB 가 대신 지워주지 않으므로 자식 테이블부터 직접 지웁니다.
     * soft delete(deleted_at) 된 행도 개인정보라 전부 지웁니다. payment 는 전자상거래법상 5년 보관이라 남깁니다.
     * 사용자 데이터를 담는 테이블을 새로 만들면 여기에 추가하세요(UserWithdrawApiIntegrationTest 가 빠진 테이블을 잡습니다).
     */
    static final List<String> DELETE_USER_DATA = List.of(
            "DELETE m FROM goal_ai_message m JOIN goal_ai_session s ON s.session_id = m.session_id WHERE s.user_id = ?",
            "DELETE FROM goal_ai_session WHERE user_id = ?",
            "DELETE c FROM goal_checkin c JOIN goal g ON g.goal_id = c.goal_id WHERE g.user_id = ?",
            "DELETE p FROM goal_plan p JOIN goal g ON g.goal_id = p.goal_id WHERE g.user_id = ?",
            "DELETE p FROM period_goal p JOIN goal g ON g.goal_id = p.goal_id WHERE g.user_id = ?",
            "DELETE m FROM milestone m JOIN goal g ON g.goal_id = m.goal_id WHERE g.user_id = ?",
            "DELETE FROM todo WHERE user_id = ?",
            "DELETE FROM schedule WHERE user_id = ?",
            "DELETE FROM goal WHERE user_id = ?",
            "DELETE FROM refresh_token WHERE user_id = ?",
            "DELETE FROM users WHERE user_id = ?");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final LoginAttemptLimiter attemptLimiter;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    /**
     * 비밀번호를 확인하고 사용자와 그 데이터를 지웁니다. 토큰을 훔친 사람이 비밀번호를 대입해 알아내지 못하도록
     * 로그인처럼 15분에 5회로 시도를 제한합니다. 리프레시 토큰도 모든 기기에서 지워져 재발급이 막히지만,
     * 이미 나간 액세스 토큰은 만료(30분)까지 필터를 통과합니다. 프론트는 탈퇴 직후 토큰을 버려야 합니다.
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        if (!attemptLimiter.tryAcquire("withdraw:" + userId)) {
            throw new BusinessException(UserErrorCode.TOO_MANY_PASSWORD_ATTEMPTS);
        }
        User user = findUser(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(UserErrorCode.PASSWORD_MISMATCH);
        }
        DELETE_USER_DATA.forEach(sql -> jdbcTemplate.update(sql, userId));
        log.info("[Withdraw] 회원탈퇴 userId={}", userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                // 토큰은 유효한데 유저가 없으면 탈퇴 직후 등 30분 이내의 액세스 토큰입니다.
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
