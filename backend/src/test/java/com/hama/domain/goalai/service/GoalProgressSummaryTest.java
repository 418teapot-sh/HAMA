package com.hama.domain.goalai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hama.domain.goalai.entity.GoalProgressSummary;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GoalProgressSummaryTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 9, 0);

    @Test
    void 변경과_예약은_새_원문을_없애지_않고_이전_토큰을_구별한다() {
        var state = GoalProgressSummary.create(1L, now);
        state.claim("first", now, 120);
        assertThat(state.running(now.plusSeconds(119))).isTrue();
        assertThat(state.running(now.plusSeconds(120))).isFalse();
        state.claim("second", now.plusSeconds(120), 120);
        assertThat(state.owns("first")).isFalse();
        assertThat(state.owns("second")).isTrue();
    }

    @Test
    void 실패해도_이전_요약을_보존하고_같은_리비전은_세_번까지만_시도한다() {
        var state = GoalProgressSummary.create(1L, now);
        state.complete("old", "이전 요약", 1, 1, now);
        state.markDirty(now.plusSeconds(1));
        for (int i = 0; i < 3; i++) {
            state.claim("token", now.plusMinutes(i + 1), 120);
            state.failed(now.plusMinutes(i + 1));
        }
        assertThat(state.getSummary()).isEqualTo("이전 요약");
        assertThat(state.getStatus()).isEqualTo("FAILED");
        assertThat(state.canClaim(now.plusHours(1))).isFalse();
        state.markDirty(now.plusHours(1));
        assertThat(state.canClaim(now.plusHours(1))).isTrue();
    }

    @Test
    void 오래된_작업을_버리면_최신_변경은_대기_상태로_남는다() {
        var state = GoalProgressSummary.create(1L, now);
        state.claim("token", now, 120);
        long claimed = state.getRevision();
        state.markDirty(now.plusSeconds(1));
        state.superseded(now.plusSeconds(2));
        assertThat(state.getRevision()).isGreaterThan(claimed);
        assertThat(state.canClaim(now.plusSeconds(2))).isTrue();
        assertThat(state.getStatus()).isEqualTo("PENDING");
    }
}
