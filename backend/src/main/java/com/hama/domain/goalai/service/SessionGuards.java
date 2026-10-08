package com.hama.domain.goalai.service;

import com.hama.domain.goalai.entity.GoalAiSession;
import com.hama.domain.goalai.entity.SessionStatus;
import com.hama.domain.goalai.exception.GoalAiErrorCode;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import java.util.Optional;

final class SessionGuards {

    private SessionGuards() {
    }

    static GoalAiSession owned(Optional<GoalAiSession> found, Long userId) {
        GoalAiSession session = found.orElseThrow(() -> new BusinessException(GoalAiErrorCode.AI_SESSION_NOT_FOUND));
        if (!session.isOwnedBy(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return session;
    }

    static void requireOpen(GoalAiSession session) {
        if (session.getStatus() == SessionStatus.CONFIRMED) {
            throw new BusinessException(GoalAiErrorCode.AI_SESSION_CONFIRMED);
        }
    }

    static void requireReady(GoalAiSession session) {
        requireOpen(session);
        if (session.getStatus() != SessionStatus.READY) {
            throw new BusinessException(GoalAiErrorCode.AI_SESSION_NOT_READY);
        }
    }
}
