package com.hama.domain.goalai.dto;

import com.hama.domain.goal.entity.GoalStatus;

public final class DraftResponses {

    private DraftResponses() {
    }

    public record DraftView(GoalDraft goalDraft) {
    }

    public record Confirmed(Long goalId, GoalStatus status) {
    }
}
