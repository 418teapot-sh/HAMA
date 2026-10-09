package com.hama.domain.user.service;

import com.hama.domain.auth.service.LoginAttemptLimiter;
import com.hama.domain.user.dto.UserResponse;
import com.hama.domain.user.entity.User;
import com.hama.domain.user.exception.UserErrorCode;
import com.hama.domain.user.repository.UserRepository;
import com.hama.global.exception.BusinessException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /*
     * 탈퇴 때 지우는 테이블입니다. 테이블 사이에 FK 가 없어서 DB 가 대신 지워주지 않으므로 자식부터 직접 지웁니다.
     * soft delete(deleted_at) 된 행도 개인정보라 전부 지웁니다. payment 는 전자상거래법상 5년 보관이라 남깁니다.
     * 사용자 데이터를 담는 테이블을 새로 만들면 여기에 추가하세요(UserWithdrawApiIntegrationTest 가 빠진 테이블을 잡습니다).
     */
    /** goal_id 로 지우는 목표의 자식 테이블 */
    static final List<String> GOAL_CHILD_TABLES = List.of("goal_checkin", "goal_plan", "period_goal", "milestone");
    /** session_id 로 지우는 AI 세션의 자식 테이블 */
    static final List<String> SESSION_CHILD_TABLES = List.of("goal_ai_message");
    /** user_id 로 지우는 테이블. 자식을 다 지운 뒤 이 순서대로 지웁니다. */
    static final List<String> USER_TABLES =
            List.of("goal_ai_session", "todo", "schedule", "goal", "refresh_token", "users");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbc;
    private final LoginAttemptLimiter attemptLimiter;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    /**
     * 비밀번호를 확인하고 사용자와 그 데이터를 지웁니다. 토큰을 훔친 사람이 비밀번호를 대입해 알아내지 못하도록
     * 로그인처럼 15분에 5회로 시도를 제한합니다. 리프레시 토큰도 모든 기기에서 지워져 재발급이 막히지만,
     * 이미 나간 액세스 토큰은 만료(30분)까지 필터를 통과합니다. 프론트는 탈퇴 직후 토큰을 버려야 합니다.
     *
     * <p>락: 다른 서비스(투두·체크인·플랜·replan)처럼 목표 행을 먼저 잠가서 잠그는 순서를 맞춥니다(교착 방지).
     * 자식 테이블은 JOIN 대신 미리 잠근 id 로 지웁니다. JOIN DELETE 는 옵티마이저가 자식 테이블을 전체 스캔하면
     * 다른 사용자 행까지 잠그기 때문입니다.
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

        Map<String, Long> byUser = Map.of("userId", userId);
        List<Long> goalIds = jdbc.queryForList(
                "SELECT goal_id FROM goal WHERE user_id = :userId FOR UPDATE", byUser, Long.class);
        List<Long> sessionIds = jdbc.queryForList(
                "SELECT session_id FROM goal_ai_session WHERE user_id = :userId FOR UPDATE", byUser, Long.class);
        deleteIn(GOAL_CHILD_TABLES, "goal_id", goalIds);
        deleteIn(SESSION_CHILD_TABLES, "session_id", sessionIds);
        USER_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE user_id = :userId", byUser));
        log.info("[Withdraw] 회원탈퇴 userId={}", userId);
    }

    private void deleteIn(List<String> tables, String column, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        tables.forEach(table ->
                jdbc.update("DELETE FROM " + table + " WHERE " + column + " IN (:ids)", Map.of("ids", ids)));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                // 토큰은 유효한데 유저가 없으면 탈퇴 직후 등 30분 이내의 액세스 토큰입니다.
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
