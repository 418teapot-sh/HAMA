package com.hama.domain.checkin.service;

import com.hama.domain.checkin.dto.CheckinResponses;
import com.hama.domain.checkin.dto.CreateCheckinRequest;
import com.hama.domain.checkin.entity.CheckinType;
import com.hama.domain.checkin.entity.GoalCheckin;
import com.hama.domain.checkin.exception.CheckinErrorCode;
import com.hama.domain.checkin.repository.CheckinRepository;
import com.hama.domain.goal.entity.Goal;
import com.hama.domain.goal.entity.GoalStatus;
import com.hama.domain.goal.exception.GoalErrorCode;
import com.hama.domain.goal.repository.GoalRepository;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import com.hama.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckinService {
    private final GoalRepository goals;
    private final CheckinRepository checkins;
    private final Clock clock;

    public CheckinService(GoalRepository goals, CheckinRepository checkins, @Qualifier("be3Clock") Clock clock) {
        this.goals = goals;
        this.checkins = checkins;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckinResponses.Created create(Long userId, CreateCheckinRequest request) {
        // 목표 삭제 및 같은 목표의 START/END 생성과 동일한 부모 행에서 직렬화합니다.
        Goal goal = owned(goals.findActiveForUpdate(request.goalId()), userId);
        if (goal.getStatus() == GoalStatus.PLANNING) {
            throw new BusinessException(CheckinErrorCode.CHECKIN_GOAL_NOT_STARTED);
        }
        GoalCheckin checkin = GoalCheckin.create(goal.getId(), request.type(), request.value(), request.note(),
                request.achieved(), request.checkedAt(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        if (request.type() != CheckinType.MID && checkins.existsByGoalIdAndType(goal.getId(), request.type())) {
            throw new BusinessException(CheckinErrorCode.CHECKIN_ALREADY_EXISTS);
        }
        return new CheckinResponses.Created(checkins.saveAndFlush(checkin).getId());
    }

    public CheckinResponses.History list(Long userId, Long goalId, Pageable pageable) {
        Goal goal = owned(goals.findActive(goalId), userId);
        return new CheckinResponses.History(goal.getUnit(), PageResponse.from(
                checkins.findByGoalIdOrderByCheckedAtDescIdDesc(goalId, pageable), CheckinResponses.Item::from));
    }

    private Goal owned(Optional<Goal> found, Long userId) {
        Goal goal = found.orElseThrow(() -> new BusinessException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(userId)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        return goal;
    }
}
